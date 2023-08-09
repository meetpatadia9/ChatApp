package com.ipsmeet.chatapp.activities

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.android.volley.Response
import com.android.volley.VolleyError
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.storage.FirebaseStorage
import com.ipsmeet.chatapp.R
import com.ipsmeet.chatapp.adapters.MessagesAdapter
import com.ipsmeet.chatapp.databinding.ActivityChatBinding
import com.ipsmeet.chatapp.databinding.LayoutDeleteBinding
import com.ipsmeet.chatapp.dataclasses.MessagesDataClass
import com.ipsmeet.chatapp.dataclasses.UserDataClass
import dmax.dialog.SpotsDialog
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class ChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatBinding
    private lateinit var databaseReference: DatabaseReference
    private lateinit var chatReference: DatabaseReference
    private lateinit var firebaseDatabase: FirebaseDatabase

    private lateinit var senderID: String
    private lateinit var receiverID: String
    private lateinit var senderRoom: String
    private lateinit var receiverRoom: String
    var chats = arrayListOf<MessagesDataClass>()
    lateinit var messagesAdapter: MessagesAdapter

    lateinit var name: String
    lateinit var message: String
    private lateinit var receiverToken: String
    private lateinit var progress: SpotsDialog
    private lateinit var byteArray: ByteArray

    lateinit var handler: Handler

    @SuppressLint("SimpleDateFormat")
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar!!.hide()
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        progress = SpotsDialog(this, R.style.Custom)
        firebaseDatabase = FirebaseDatabase.getInstance()

        senderID = FirebaseAuth.getInstance().currentUser!!.uid   // ID of logged-in user
        receiverID = intent.getStringExtra("userID").toString()   // ID of other person
        receiverToken = intent.getStringExtra("token").toString()   // TOKEN of other person

        //  creating room, for chat, to store data users-vise
        senderRoom = senderID + receiverID
        receiverRoom = receiverID + senderID

        personsStatus()

        binding.commsBack.setOnClickListener {
            updateUI()
        }

        binding.commsProfile.setOnClickListener {
            openProfile()
        }

        binding.commsName.setOnClickListener {
            openProfile()
        }

        //  MESSAGE ADAPTER
        val linearLayoutManager = LinearLayoutManager(this)
        linearLayoutManager.stackFromEnd = true
        messagesAdapter = MessagesAdapter(this@ChatActivity, chats,
        object : MessagesAdapter.MessageActionListener {
            override fun longPressDelete(senderID: MessagesDataClass) {      // long-press on message
                val bindingDialog = LayoutDeleteBinding.inflate(LayoutInflater.from(this@ChatActivity))
                val dialog = Dialog(this@ChatActivity)
                dialog.setContentView(bindingDialog.root)
                dialog.window!!.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                dialog.show()

                bindingDialog.deleteYes.setOnClickListener {
                    FirebaseDatabase.getInstance().getReference("Chats/$senderRoom/Messages")
                        .addValueEventListener(object : ValueEventListener {
                                override fun onDataChange(snapshot: DataSnapshot) {
                                    if (snapshot.exists()) {
                                        for (i in snapshot.children) {
                                            val listData = i.getValue(MessagesDataClass::class.java)
                                            listData?.key = i.key.toString()

                                            if (senderID.key == listData?.key) {
                                                FirebaseDatabase.getInstance().getReference("Chats/$senderRoom/Messages/${listData.key}").removeValue()
                                                FirebaseDatabase.getInstance().getReference("Chats/$receiverRoom/Messages/${listData.key}").removeValue()
                                                dialog.dismiss()
                                            }
                                        }
                                    }
                                }

                                override fun onCancelled(error: DatabaseError) {
                                    Log.d("DatabaseError", error.message)
                                }
                            }
                        )
                }

                bindingDialog.deleteNo.setOnClickListener {
                    dialog.dismiss()
                }
            }
        })

        binding.commsRecyclerView.apply {
            layoutManager = linearLayoutManager
            adapter = messagesAdapter
        }

        //  FETCHING USER DATA, WHO'S CHAT IS OPEN
        databaseReference = FirebaseDatabase.getInstance().getReference("Users/$receiverID")
        databaseReference.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val data = snapshot.getValue(UserDataClass::class.java)
                    data!!.key = snapshot.key.toString()
                    binding.commsName.text = data.userName

                    //  FETCHING USER PROFILE FROM FIREBASE-STORAGE
                    val localFile = File.createTempFile("tempFile", "jpeg")
                    FirebaseStorage.getInstance()
                        .getReference("Images/*${snapshot.key}").getFile(localFile)
                        .addOnSuccessListener {
                            val bitmap = BitmapFactory.decodeFile(localFile.absolutePath)
                            Glide.with(applicationContext).load(bitmap).into(binding.commsProfile)
                        }
                        .addOnFailureListener {
                            Log.d("Fail to load user profiles", it.message.toString())
                        }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.d("ChatActivity ~ Failed", error.message)
            }
        })

        //  FETCHING CHATS
        chatReference = FirebaseDatabase.getInstance().getReference("Chats/$senderRoom/Messages")
        chatReference.addValueEventListener(object : ValueEventListener {
            @SuppressLint("NotifyDataSetChanged")
            override fun onDataChange(snapshot: DataSnapshot) {
                chats.clear()
                if (snapshot.exists()) {
                    for (msgs in snapshot.children) {
                        val comms = msgs.getValue(MessagesDataClass::class.java)
                        comms!!.key = msgs.key.toString()
                        chats.add(comms)
                        message = comms.message
                    }
                    val recyclerview = binding.commsRecyclerView
                    val position = recyclerview.adapter?.itemCount
                    binding.commsRecyclerView.smoothScrollToPosition(position!! - 1)
                    messagesAdapter.notifyDataSetChanged()
                }
                else {
                    /*
                        Avoid Error --> java.lang.IndexOutOfBoundsException: Index: 0, Size: 0
                        prevents activity to crash, when we clear all chat and there is no messages to display
                    */
                    messagesAdapter.notifyDataSetChanged()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.d("Failed to load chats", error.message)
            }
        })

        //  FETCHING LOGGED-IN USER'S NAME
        FirebaseDatabase.getInstance().getReference("Users/$senderID")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val u = snapshot.getValue(UserDataClass::class.java)
                    name = u!!.userName
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.d("Failed to fetch logged-in user's name", error.message)
                }
            })

        //  `TextWatcher` on edittext
        binding.commsTypeMsg.addTextChangedListener(messageSend)

        //  send message in chat
        binding.btnSendMsg.setOnClickListener {
            chats.clear()
            val msg = MessagesDataClass(
                message = binding.commsTypeMsg.text.toString().trim(),
                senderID = senderID,
                timeStamp = SimpleDateFormat("hh:mm aa").format(Calendar.getInstance().time)
            )

            val pushKey = FirebaseDatabase.getInstance().getReference("Chats/$senderRoom/Messages").push().key

            //  CREATING CHAT-ROOMS TO STORE CHATS
            firebaseDatabase.getReference("Chats")
                .child(senderRoom)
                .child("Messages")
                .child(pushKey!!)
                .setValue(msg)
                .addOnCompleteListener {
                    firebaseDatabase.getReference("Chats")
                        .child(receiverRoom)
                        .child("Messages")
                        .child(pushKey)
                        .setValue(msg)
                        .addOnSuccessListener {
                            sendNotification(name, message, receiverToken)  // passing required parameters to send notification
                        }
                }
            binding.commsTypeMsg.setText("")
        }

        //  image from gallery
        binding.sendAttach.setOnClickListener {
            selectImage()
        }

        //  image from camera
        binding.sendCam.setOnClickListener {
            openCamera()
        }
    }

    private fun personsStatus() {
        FirebaseDatabase.getInstance().getReference("Chats/$senderRoom/Status/$receiverID")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.value.toString() == "In Chat") {
                        binding.commsStatus.text = snapshot.value.toString()
                    }
                    else if (snapshot.value.toString() == "Typing...") {
                        binding.commsStatus.text = snapshot.value.toString()
                    }
                    else {
                        FirebaseDatabase.getInstance().getReference("Active Users/$receiverID")
                            .addValueEventListener(object : ValueEventListener {
                                override fun onDataChange(snapshot: DataSnapshot) {
                                    binding.commsStatus.text = snapshot.value.toString()
                                }

                                override fun onCancelled(error: DatabaseError) {
                                    Log.d("status error", error.message)
                                }
                            })
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.d("status error", error.message)
                }
            })
    }

    /*
        IMAGE FROM CAMERA
    */
    private fun openCamera() {
        clickPhoto.launch(
            Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        )
    }

    @SuppressLint("SimpleDateFormat")
    private val clickPhoto: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            progress.show()

            val photo = result.data!!.extras!!["data"] as Bitmap
            val baos = ByteArrayOutputStream()
            photo.compress(Bitmap.CompressFormat.JPEG, 100, baos)
            byteArray = baos.toByteArray()  // to upload we need `ByteArray`

            val imgKey = FirebaseDatabase.getInstance().getReference("Chats").push().key

            val storageReference = FirebaseStorage.getInstance().getReference("Chat Media/$imgKey")
            storageReference.putBytes(byteArray)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        FirebaseStorage.getInstance().getReference("Chat Media/$imgKey").downloadUrl
                            .addOnSuccessListener { uri ->
                                val filePath = uri.toString()
                                Log.d("filePath", filePath)

                                chats.clear()
                                val msg = MessagesDataClass(
                                    key = imgKey!!,
                                    message = "",
                                    senderID = senderID,
                                    imgURL = filePath,
                                    timeStamp = SimpleDateFormat("hh:mm aa").format(Calendar.getInstance().time)
                                )

                                firebaseDatabase.getReference("Chats")
                                    .child(senderRoom)
                                    .child("Messages")
                                    .child(imgKey)
                                    .setValue(msg)
                                    .addOnCompleteListener {
                                        firebaseDatabase.getReference("Chats")
                                            .child(receiverRoom)
                                            .child("Messages")
                                            .child(imgKey)
                                            .setValue(msg)
                                            .addOnSuccessListener {
                                                sendNotification(name, "Photo", receiverToken)  // passing required parameters to send notification
                                                progress.dismiss()
                                            }
                                    }
                            }
                            .addOnFailureListener { e ->
                                Log.d("fail filePath", e.message.toString())
                                progress.dismiss()
                            }
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Fail", Toast.LENGTH_SHORT).show()
                }
        }

    /*
        IMAGE FROM GALLERY
    */
    private fun selectImage() {
        val intent = Intent()
        intent.type = "image/*"
        intent.action = Intent.ACTION_GET_CONTENT
        imagePicker.launch(intent)
    }

    @SuppressLint("SimpleDateFormat")
    private val imagePicker: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            progress.show()
            val imgURI = result.data?.data!!
            Log.d("imgURI", imgURI.toString())

            val imgKey = FirebaseDatabase.getInstance().getReference("Chats").push().key

            //  SEND IMAGE IN CHAT
            FirebaseStorage.getInstance().getReference("Chat Media/$imgKey").putFile(imgURI)
                .addOnCompleteListener {
                    if (it.isSuccessful) {
                        FirebaseStorage.getInstance().getReference("Chat Media/$imgKey").downloadUrl
                            .addOnSuccessListener { uri ->
                                val filePath = uri.toString()
                                Log.d("filePath", filePath)

                                chats.clear()
                                val msg = MessagesDataClass(
                                    key = imgKey!!,
                                    message = "",
                                    senderID = senderID,
                                    imgURL = filePath,
                                    timeStamp = SimpleDateFormat("hh:mm aa").format(Calendar.getInstance().time)
                                )

                                firebaseDatabase.getReference("Chats")
                                    .child(senderRoom)
                                    .child("Messages")
                                    .child(imgKey)
                                    .setValue(msg)
                                    .addOnCompleteListener {
                                        firebaseDatabase.getReference("Chats")
                                            .child(receiverRoom)
                                            .child("Messages")
                                            .child(imgKey)
                                            .setValue(msg)
                                            .addOnSuccessListener {
                                                sendNotification(name, "Photo", receiverToken)  // passing required parameters to send notification
                                                progress.dismiss()
                                            }
                                    }
                            }
                            .addOnFailureListener { e ->
                                Log.d("fail filePath", e.message.toString())
                                progress.dismiss()
                            }
                    }

                }
                .addOnFailureListener {
                    Toast.makeText(this, "Fail", Toast.LENGTH_SHORT).show()
                }
        }

    //  TEXT-WATCHER FOR SEND BUTTON, SO USER CANNOT TRIGGER BUTTON EVENT WITHOUT EMPTY EDIT_TEXTVIEW
    private var messageSend: TextWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            // default Button should be disable (before entering text/message in EditTextView)
            binding.btnSendMsg.isEnabled = false
        }

        override fun onTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            // when we give some text/message (only whitespace is not included) to EditTextView, Button should be enabled
            binding.btnSendMsg.isEnabled = binding.commsTypeMsg.text.trim().isNotEmpty()
        }

        override fun afterTextChanged(s: Editable?) {
            FirebaseDatabase.getInstance().reference.child("Chats/$receiverRoom/Status/$senderID").setValue("Typing...")
            // if user make some changes in entered text/message (only whitespace is not included)
            // if user clear EditTextView, then Button will be disable
            binding.btnSendMsg.isEnabled = binding.commsTypeMsg.text.trim().isNotEmpty()

            handler = Handler()
            handler.removeCallbacksAndMessages(null)
            handler.postDelayed(userStoppedTyping, 2000)
        }

        val userStoppedTyping = Runnable {
            FirebaseDatabase.getInstance().reference.child("Chats/$receiverRoom/Status/$senderID").setValue("In Chat")
        }
    }

    private fun openProfile() {
        startActivity(
            Intent(this@ChatActivity, ViewProfileActivity::class.java)
                .putExtra("userID", receiverID)
                .putExtra("token", receiverToken)
        )
        finish()
    }

    private fun updateUI() {
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)   // all of the other activities on top of it will be closed
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)    // activity will become the start of a new task on this history stack
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)  // activity becomes the new root of an otherwise empty task, and any old activities are finished
        )
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        super.onBackPressed()
        updateUI()
    }

    private fun sendNotification(name: String, message: String, token: String) {
        val requestQueue = Volley.newRequestQueue(this)     // using Volley

        val url = "https://fcm.googleapis.com/fcm/send"

        /*  BODY/RAW DATA FORMAT THAT WE NEED
        {
            "to" : "dJqvGFCbSsq7EPPnJKBJ8D:APA91bELNZ75QamVQVXCcarFlGgkFZQ0zk3SGw7pT_y6mvyyfybaQkaDb-qHYKlVzGKU2w_37e9qVfQ4iSl2TaN0hiENpJBy7rtuL0tSScu-ktAtPQKKmPDlPjexo0epaWU47880hc5R",
            "collapse_key" : "type_a",
            "notification" : {
                "title": "Sender Name",
                "body" : "Received message"
            },
            "data" : {
                "title": "Title of Your Notification in Title",
                "body" : "Click here"
            }
        }
        */
        val jsonObject = JSONObject()
        jsonObject.put("title", name)
        jsonObject.put("body", message)

        val data = JSONObject()
        data.put("title", "Title of Your Notification in Title")
        data.put("body", "Click here")

        //  combining all the data into one as `notificationData`
        val notificationData = JSONObject()
        notificationData.put("to", token)   //  `here `token` is the token of other person
        notificationData.put("collapse_key", "type_a")
        notificationData.put("notification", jsonObject)    //  notification data
        notificationData.put("data", data)

        //  adding `Header` in request
        val jsonObjectRequest = object : JsonObjectRequest(Method.POST, url, notificationData,
            object : Response.Listener<JSONObject?> {
                override fun onResponse(response: JSONObject?) {
                    Log.d("onResponse", response.toString())
                }
            },
            object : Response.ErrorListener {
                override fun onErrorResponse(error: VolleyError?) {
                    Log.d( "jsonObjectRequest Error", error!!.message.toString())
                }
            }) {

            override fun getHeaders(): MutableMap<String, String> {
                val map = HashMap<String, String>()
                map["Authorization"] = "key=AAAAicac9VA:APA91bGFvmRwEgcFKy6jEgdvldoy8JWhWiX2SEPCG-jsSG805wfhcUqgwJAQxT4KR8nz7aAMomB00cUnwDevNTjBZ3OR4D6u1hjs3Jcw-Bhp5ghZuTUaFgirQE5uv3AwDR5606yUBJu6"   // server key
                map["Content-Type"] = "application/json"
                return map
            }
        }
        requestQueue.add(jsonObjectRequest)
    }

    override fun onResume() {
        super.onResume()
        //  IF USER IS ACTIVE ON APP, STORE IT AS ACTIVE USER
        FirebaseDatabase.getInstance().reference.child("Active Users").child(senderID).setValue("Online")
        FirebaseDatabase.getInstance().getReference("Chats/$senderRoom/Status/$senderID").setValue("In Chat")
        FirebaseDatabase.getInstance().getReference("Chats/$receiverRoom/Status/$senderID").setValue("In Chat")
        personsStatus()
    }

    @SuppressLint("SimpleDateFormat")
    override fun onStop() {
        super.onStop()
        FirebaseDatabase.getInstance().reference.child("Chats/$receiverRoom/Status/$senderID")
            .setValue("Last seen at ${ SimpleDateFormat("hh:mm aa").format(Calendar.getInstance().time) }")

        FirebaseDatabase.getInstance().reference.child("Chats/$senderRoom/Status/$senderID")
            .setValue("Last seen at ${ SimpleDateFormat("hh:mm aa").format(Calendar.getInstance().time) }")
    }

}