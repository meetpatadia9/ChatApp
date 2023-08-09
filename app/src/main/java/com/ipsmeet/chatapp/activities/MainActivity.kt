package com.ipsmeet.chatapp.activities

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.*
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.storage.FirebaseStorage
import com.ipsmeet.chatapp.R
import com.ipsmeet.chatapp.adapters.ChatAdapter
import com.ipsmeet.chatapp.adapters.FoundUserAdapter
import com.ipsmeet.chatapp.databinding.ActivityMainBinding
import com.ipsmeet.chatapp.databinding.LayoutAddFriendsBinding
import com.ipsmeet.chatapp.databinding.LayoutDialogBinding
import com.ipsmeet.chatapp.databinding.PopupViewProfileBinding
import com.ipsmeet.chatapp.dataclasses.UserDataClass
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var bindingDialog: LayoutAddFriendsBinding

    lateinit var userID: String
    private lateinit var auth: FirebaseAuth
    private lateinit var databaseReference: DatabaseReference

    var listPhoneNumbers = arrayListOf<String>()
    var chatData = arrayListOf<UserDataClass>()
    var friendList = arrayListOf<String>()
    lateinit var foundUsersKey: String
    lateinit var dialog: Dialog

    private lateinit var senderRoom: String
    private lateinit var receiverRoom: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        foundUsersKey = ""
        auth = FirebaseAuth.getInstance()
        userID = auth.currentUser!!.uid

        FirebaseMessaging.getInstance().token
            .addOnSuccessListener {
                val userToken = HashMap<String, Any>()
                userToken["token"] = it.toString()  // userToken.put("token", it.toString())

                FirebaseDatabase.getInstance().getReference("Users/$userID").updateChildren(userToken)
            }

        //  DISPLAYING FRIENDS AS CHAT
        databaseReference = FirebaseDatabase.getInstance().getReference("Users/$userID/Friend List")
        databaseReference.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                chatData = ArrayList()
                if (snapshot.exists()) {
                    for (item in snapshot.children) {
                        FirebaseDatabase.getInstance().getReference("Users").orderByChild("userName")
                            .addValueEventListener(object : ValueEventListener {
                                override fun onDataChange(snapshot: DataSnapshot) {
                                    if (snapshot.exists()) {
                                        for (i in snapshot.children) {
                                            if (i.key.toString() == item.value.toString()) {
                                                val showData = i.getValue(UserDataClass::class.java)
                                                showData!!.key = i.key.toString()
                                                chatData.add(showData)

                                                binding.mainRecyclerView.apply {
                                                    layoutManager = LinearLayoutManager(this@MainActivity, LinearLayoutManager.VERTICAL,false)
                                                    adapter = ChatAdapter(this@MainActivity, chatData,
                                                            object : ChatAdapter.OnClick {
                                                                //  open chat
                                                                override fun openChat(key: String, token: String) {
                                                                    viewChat(key, token)
                                                                }

                                                                //  open dialog-box to show profile image
                                                                override fun viewProfilePopup(key: String, token: String) {
                                                                    val bindDialog: PopupViewProfileBinding = PopupViewProfileBinding.inflate(LayoutInflater.from(this@MainActivity))

                                                                    val dialog = Dialog(this@MainActivity)
                                                                    dialog.setContentView(bindDialog.root)
                                                                    dialog.show()

                                                                    FirebaseDatabase.getInstance().getReference("Users/$key")
                                                                        .addValueEventListener(
                                                                            object : ValueEventListener {
                                                                                override fun onDataChange(snapshot: DataSnapshot) {
                                                                                    if (snapshot.exists()) {
                                                                                        val data = snapshot.getValue(UserDataClass::class.java)
                                                                                        data!!.key = snapshot.key.toString()
                                                                                        bindDialog.popupViewName.text = data.userName

                                                                                        //  FETCHING USER PROFILE FROM FIREBASE-STORAGE
                                                                                        val localFile = File.createTempFile("tempFile","jpeg")
                                                                                        FirebaseStorage.getInstance().getReference("Images/*${key}").getFile(localFile)
                                                                                            .addOnSuccessListener {
                                                                                                val bitmap = BitmapFactory.decodeFile(localFile.absolutePath)
                                                                                                Glide.with(applicationContext).load(bitmap).into(bindDialog.popupViewProfile)
                                                                                            }
                                                                                            .addOnFailureListener {
                                                                                                Log.d("Fail to load user profiles", it.message.toString())
                                                                                            }
                                                                                    }
                                                                                }

                                                                                override fun onCancelled(error: DatabaseError) {
                                                                                    Log.d("popup failed", error.message)
                                                                                }
                                                                            })

                                                                    bindDialog.popupSendMsg.setOnClickListener {
                                                                        dialog.dismiss()
                                                                        viewChat(key, token)
                                                                    }

                                                                    bindDialog.popupInfo.setOnClickListener {
                                                                        dialog.dismiss()
                                                                        viewProfile(key, token)
                                                                    }
                                                                }
                                                            })
                                                    addItemDecoration(DividerItemDecoration(this@MainActivity, DividerItemDecoration.VERTICAL))
                                                }
                                            }
                                        }
                                    }
                                }

                                override fun onCancelled(error: DatabaseError) {
                                    Log.d("MainActivity ~ Database Error", error.message)
                                }
                            })
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.d("MainActivity ~ Database Error", error.message)
            }
        })

        //  ADD FRIENDS
        binding.mainBtnFAB.setOnClickListener {
            bindingDialog = LayoutAddFriendsBinding.inflate(LayoutInflater.from(this))

            dialog = Dialog(this)
            dialog.setContentView(bindingDialog.root)
            dialog.window!!.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            dialog.show()

            bindingDialog.layoutSearch.setOnClickListener {
                var matchedNumber = ""

                //  FETCHING ALL THE PHONE NUMBERS
                FirebaseDatabase.getInstance().getReference("Users")
                    .addValueEventListener(object : ValueEventListener {
                        override fun onDataChange(snapshot: DataSnapshot) {
                            if (snapshot.exists()) {
                                for (i in snapshot.children) {
                                    val user = i.getValue(UserDataClass::class.java)
                                    user!!.key = i.key.toString()
                                    foundUsersKey = user.key
                                    //  AND STORING THAT PHONE NUMBER IN ARRAY-LIST
                                    listPhoneNumbers.add(user.phoneNumber)

                                    //  IF ENTERED NUMBER EXISTS IN THE LIST
                                    for (num in listPhoneNumbers) {
                                        if (bindingDialog.edtxtFindPhone.text.toString() == num) {
                                            matchedNumber = num     // STORE NUMBER AS `matchedNumber`
                                        }
                                    }

                                    //  FETCH USER'S userID AS PER `matchedNumber`
                                    if (matchedNumber == user.phoneNumber) {
                                        bindingDialog.recyclerViewFoundUSer.apply {
                                            layoutManager = LinearLayoutManager(dialog.context, LinearLayoutManager.VERTICAL, false)
                                            adapter = FoundUserAdapter(dialog.context, user,
                                                object : FoundUserAdapter.OnClick {     // AND STORE IT IN `Friend List`
                                                    override fun clickListener(key: String) {
                                                        FirebaseDatabase.getInstance()
                                                            .getReference("Users/$userID")
                                                            .child("Friend List")
                                                            .push()
                                                            .setValue(key)
                                                        dialog.dismiss()
                                                    }
                                                })
                                        }
                                    }
                                }
                            }
                        }

                        override fun onCancelled(error: DatabaseError) {
                            Log.d("Failed to find user", error.message)
                        }
                    })

                //  IF USER IS ALREADY ADDED AS FRIEND
                /*FirebaseDatabase.getInstance().getReference("Users/$userID/Friend List")
                    .addValueEventListener(object : ValueEventListener {
                        override fun onDataChange(snapshot: DataSnapshot) {
                            if (snapshot.exists()) {
                                for (n in snapshot.children) {
                                    val fList = n.value.toString()
                                    friendList.add(fList)
                                }

                                for (a in friendList) {
                                    if (foundUsersKey == a) {
                                        dialog.dismiss()
                                        val d = Dialog(this@MainActivity)
                                        d.setContentView(R.layout.layout_user_exists)
                                        d.show()
                                        break
                                    }
                                }
                            }
                        }

                        override fun onCancelled(error: DatabaseError) {
                            Log.d("Failed to load Friend List", error.message)
                        }
                    })*/
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_profile -> {
                startActivity(
                    Intent(this, ProfileActivity::class.java)
                )
            }

            R.id.menu_signOut -> {
                signOut()
            }
        }

        return super.onOptionsItemSelected(item)
    }

    private fun signOut() {
        val bindDialog: LayoutDialogBinding = LayoutDialogBinding.inflate(LayoutInflater.from(this))

        val dialog = Dialog(this)
        dialog.setContentView(bindDialog.root)
        dialog.window!!.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.show()

        bindDialog.signOutYes.setOnClickListener {
            Firebase.auth.signOut()
            dialog.dismiss()
            updateUI()
        }

        bindDialog.signOutNo.setOnClickListener {
            dialog.dismiss()
        }
    }

    //  SIGN-OUT, JUMP TO LOGIN PAGE
    private fun updateUI() {
        startActivity(
            Intent(this, SignInActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)   // all of the other activities on top of it will be closed
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)    // activity will become the start of a new task on this history stack
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)  // activity becomes the new root of an otherwise empty task, and any old activities are finished
        )
        finish()
    }

    //  OPEN VIEW PROFILE ACTIVITY
    private fun viewProfile(key: String, token: String) {
        startActivity(
            Intent(this@MainActivity, ViewProfileActivity::class.java)
                .putExtra("userID", key)
                .putExtra("token", token)
        )
    }

    //  OPEN CHAT ACTIVITY
    private fun viewChat(key: String, token: String) {
        //  creating room, for chat, to store data users-vise
        senderRoom = userID + key
        receiverRoom = key + userID

        FirebaseDatabase.getInstance().getReference("Chats/$senderRoom/Status").child(userID).setValue("In Chat")
            .addOnCompleteListener {
                FirebaseDatabase.getInstance().getReference("Chats/$receiverRoom/Status").child(userID).setValue("In Chat")
            }

        startActivity(
            Intent(this@MainActivity, ChatActivity::class.java)
                .putExtra("userID", key)
                .putExtra("token", token)
        )
    }

    override fun onResume() {
        super.onResume()
        chatData.clear()
        //  IF USER IS ACTIVE ON APP, STORE IT AS ACTIVE USER
        FirebaseDatabase.getInstance().reference.child("Active Users").child(userID).setValue("Online")
    }

    @SuppressLint("SimpleDateFormat")
    override fun onDestroy() {
        super.onDestroy()
        //  IF USER CLOSE APP, STORES THE LAST SEEN OF USER
        FirebaseDatabase.getInstance().reference.child("Active Users").child(userID)
            .setValue("Last seen at ${ SimpleDateFormat("hh:mm aa").format(Calendar.getInstance().time) }")
    }

    @SuppressLint("SimpleDateFormat")
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        //  IF USER MINIMIZE APP, STORES THE LAST SEEN OF USER
        FirebaseDatabase.getInstance().reference.child("Active Users").child(userID)
            .setValue("Last seen at ${ SimpleDateFormat("hh:mm aa").format(Calendar.getInstance().time) }")
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        super.onBackPressed()
        finish()
    }

}

/*
                    usersFetched.clear()
                    chatData.clear()
                    if (snapshot.exists()) {
                        for (users in snapshot.children) {
                            val data = users.getValue(UserDataClass::class.java)
                            data?.key = users?.key.toString()
                            usersFetched.add(data!!)
                            listPhoneNumbers.add(data.phoneNumber)
                            Log.d("usersFetched", usersFetched.toString())
                            Log.d("data?.key", users.key.toString().toString())

                        }
                        Log.d("usersFetched display", usersFetched.toString())
                        Log.d("friendList display", friendList.toString())

                        Log.d("usersFetched.size-1 display", (friendList.size - 1).toString())

                        for (i in 0..(friendList.size - 1)) {

                            Log.d("friendList[i]", friendList[i])

                            for (j in 0..usersFetched.size - 1) {
                                Log.d("usersFetched[j]", usersFetched[j].toString())
                                if (usersFetched[j].key == friendList[i] && userID != friendList[i]) {
                                    chatData.add(usersFetched[j])
                                }
                            }
                        }
                        Log.d("usersFetched", usersFetched.toString())
                        Log.d("chatData.toString()", chatData.toString())
                        binding.mainRecyclerView.apply {
                            layoutManager = LinearLayoutManager(
                                this@MainActivity,
                                LinearLayoutManager.VERTICAL,
                                false
                            )
                            adapter = ChatAdapter(this@MainActivity, chatData,
                                object : ChatAdapter.OnClick {
                                    override fun openChat(key: String, token: String) {

                                    }

                                    override fun viewProfilePopup(key: String, token: String) {
                                        TODO("Not yet implemented")
                                    }
                                })
                        }

                    }*/

/*
        //  ADD FRIENDS
        /*binding.mainBtnFAB.setOnClickListener {
            bindingDialog = LayoutAddFriendsBinding.inflate(LayoutInflater.from(this))

            dialog = Dialog(this)
            dialog.setContentView(bindingDialog.root)
            dialog.window!!.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            dialog.show()

            bindingDialog.layoutSearch.setOnClickListener {
                var matchedNumber = ""

                //  FETCHING ALL THE PHONE NUMBERS
                FirebaseDatabase.getInstance().getReference("Users")
                    .addValueEventListener(object : ValueEventListener {
                        override fun onDataChange(snapshot: DataSnapshot) {
                            if (snapshot.exists()) {
                                var user = UserDataClass()
                                var isAlreaduyAdded = true
                                val userData = UserDataClass()
                                for (i in snapshot.children) {
                                    user = i.getValue(UserDataClass::class.java)!!
                                    user!!.key = i.key.toString()
                                    foundUsersKey = user.key

                                    //  AND STORING THAT PHONE NUMBER IN ARRAY-LIST
                                    listPhoneNumbers.add(user.phoneNumber)

                                    //  IF ENTERED NUMBER EXISTS IN THE LIST
                                    val userNumber =
                                        FirebaseAuth.getInstance().currentUser?.phoneNumber
                                    for (num in listPhoneNumbers) {
                                        Log.d("num", num.toString())
                                        if (bindingDialog.edtxtFindPhone.text.toString() == num && bindingDialog.edtxtFindPhone.text.toString() != userNumber) {
                                            matchedNumber = num     // STORE NUMBER AS `matchedNumber`
                                            Log.d("matchedNumber", matchedNumber.toString())
                                            isAlreaduyAdded = false
                                            break
                                        }
                                    }

                                    for( number in chatData ){
                                        if(number.phoneNumber.equals(matchedNumber)){
                                            isAlreaduyAdded = false
                                        }else{
                                            is
                                        }
                                    }

                                    //  FETCH USER'S userID AS PER `matchedNumber`
                                    //  IF USER IS ALREADY ADDED AS FRIEND
                                 *//*   FirebaseDatabase.getInstance().getReference("Users/$userID/Friend List")
                                        .addValueEventListener(object : ValueEventListener {
                                            override fun onDataChange(snapshot: DataSnapshot) {
                                                if (snapshot.exists()) {
                                                    for (n in snapshot.children) {
                                                        val fList = n.value.toString()
                                                        friendList.add(fList)
                                                    }

                                                    for (a in friendList) {
                                                        if (foundUsersKey == a) {
                                                            dialog.dismiss()
                                                            break
                                                        }
                                                    }
                                                }
                                            }

                                            override fun onCancelled(error: DatabaseError) {
                                                Log.d("Failed to load Friend List", error.message)
                                            }
                                        })

                                       else{
                                            continue
                                        }*/
                                    if(isAlreaduyAdded){
                                        bindingDialog.recyclerViewFoundUSer.apply {
                                            layoutManager = LinearLayoutManager(
                                                dialog.context,
                                                LinearLayoutManager.VERTICAL,
                                                false
                                            )
                                            adapter = FoundUserAdapter(dialog.context, user,
                                                object :
                                                    FoundUserAdapter.OnClick {     // AND STORE IT IN `Friend List`
                                                    override fun clickListener(key: String) {
                                                        FirebaseDatabase.getInstance()
                                                            .getReference("Users/$userID")
                                                            .child("Friend List")
                                                            .push()
                                                            .setValue(key)
                                                        dialog.dismiss()
                                                    }
                                                })
                                        }
                                    }else{
                                        val d = Dialog(this@MainActivity)
                                        d.setContentView(R.layout.layout_user_exists)
                                        d.show()
                                    }

                                }

                            }
                        }

                        override fun onCancelled(error: DatabaseError) {
                            Log.d("Failed to find user", error.message)
                        }
                    })

            }
        }*/