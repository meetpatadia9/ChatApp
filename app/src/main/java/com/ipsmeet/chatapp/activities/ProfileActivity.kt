package com.ipsmeet.chatapp.activities

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.storage.FirebaseStorage
import com.ipsmeet.chatapp.R
import com.ipsmeet.chatapp.databinding.ActivityProfileBinding
import com.ipsmeet.chatapp.dataclasses.SignInDataClass
import dmax.dialog.SpotsDialog
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class ProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileBinding
    private lateinit var userID: String
    private lateinit var progress: SpotsDialog
    private lateinit var photo: Bitmap
    private lateinit var byteArray: ByteArray
    private lateinit var imgURI: Uri
    private lateinit var databaseReference: DatabaseReference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar!!.hide()
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        progress = SpotsDialog(this, R.style.Custom2)

        userID = FirebaseAuth.getInstance().currentUser!!.uid

        binding.userBack.setOnClickListener {
            updateUI()
        }

        //  FETCHING USER DETAILS
        databaseReference = FirebaseDatabase.getInstance().getReference("Users/$userID")
        databaseReference.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                //  fetching user's profile image from firebase-storage
                val localFile = File.createTempFile("tempFile", "jpeg")
                FirebaseStorage.getInstance()
                    .getReference("Images/*${snapshot.key}").getFile(localFile)
                    .addOnSuccessListener {
                        val bitmap = BitmapFactory.decodeFile(localFile.absolutePath)
                        Glide.with(applicationContext).load(bitmap).into(binding.userProfileImg)
                    }
                    .addOnFailureListener {
                        Log.d("Fail to load user profiles", it.message.toString())
                    }

                //  fetching user's `name`, `phone-number` and `about` from firebase-realtime-database
                val userProfile = snapshot.getValue(SignInDataClass::class.java)
                binding.userTxtName.text = userProfile!!.userName
                if (userProfile.about == "") {      //  if `about` is empty then load default `about`, else show user's `about`
                    binding.userTxtAbout.text = getString(R.string.default_about)
                }
                else {
                    binding.userTxtAbout.text = userProfile.about
                }
                binding.userTxtPhone.text = userProfile.phoneNumber
            }

            override fun onCancelled(error: DatabaseError) {
                Log.d("ProfileActivity ~ failed to load", error.message)
            }
        })

        binding.userEditImg.setOnClickListener {
            updateIMG()
        }

        binding.userEditName.setOnClickListener {
            updateNAME()
        }

        binding.userEditAbout.setOnClickListener {
            updateABOUT()
        }

    }

    @SuppressLint("QueryPermissionsNeeded")
    private fun updateIMG() {
        val bottomSheetDialog = BottomSheetDialog(this)
        bottomSheetDialog.setContentView(R.layout.bsd_edit_img)
        bottomSheetDialog.show()

        //  SELECT IMAGE FROM CAMERA
        bottomSheetDialog.findViewById<ConstraintLayout>(R.id.updateUser_layoutCam)
            ?.setOnClickListener {
                val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)  // have the camera application capture an image and return it
//                    .putExtra(MediaStore.EXTRA_OUTPUT,  MediaStore.Images.Media.EXTERNAL_CONTENT_URI.toString())

                if (cameraIntent.resolveActivity(packageManager) != null) {
                    clickPhoto.launch(cameraIntent)
                    startActivityForResult(cameraIntent, 1)
                }

                bottomSheetDialog.dismiss()
            }

        //  SELECT IMAGE FROM GALLERY
        bottomSheetDialog.findViewById<ConstraintLayout>(R.id.updateUser_layoutGallery)
            ?.setOnClickListener {
                selectImage()
                bottomSheetDialog.dismiss()
            }
    }

    //  IMAGE FROM CAMERA
    private val clickPhoto: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        photo = result.data!!.extras!!["data"] as Bitmap
        binding.userProfileImg.setImageBitmap(photo)

        binding.userProfileImg.isDrawingCacheEnabled = true
        binding.userProfileImg.buildDrawingCache()
        val bitmap = (binding.userProfileImg.drawable as BitmapDrawable).bitmap
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, baos)
        byteArray = baos.toByteArray()  // to upload we need `ByteArray`
        uploadImageBITMAP()
    }

    private fun uploadImageBITMAP() {
        progress.show()

        val storageReference = FirebaseStorage.getInstance().getReference("Images/*$userID")
        storageReference.putBytes(byteArray)
            .addOnSuccessListener {
                progress.dismiss()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Fail", Toast.LENGTH_SHORT).show()
            }
    }

    //  IMAGE FROM GALLERY
    private fun selectImage() {
        val intent = Intent()
        intent.type = "image/*"
        intent.action = Intent.ACTION_GET_CONTENT
        imagePicker.launch(intent)
    }

    private val imagePicker: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            imgURI = result.data?.data!!
            binding.userProfileImg.setImageURI(imgURI)

            progress.show()

            val storageReference = FirebaseStorage.getInstance().getReference("Images/*$userID")
            storageReference.putFile(imgURI)
                .addOnSuccessListener {
                    progress.dismiss()
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Fail", Toast.LENGTH_SHORT).show()
                }
        }

    private fun updateNAME() {
        val bottomSheetDialog = BottomSheetDialog(this, R.style.BottomSheetDialog)
        bottomSheetDialog.setContentView(R.layout.bsd_edit_name)
        bottomSheetDialog.show()

        val name = bottomSheetDialog.findViewById<EditText>(R.id.user_edtxtName)
        name!!.setText(binding.userTxtName.text.toString())

        bottomSheetDialog.findViewById<TextView>(R.id.user_txtSave)
            ?.setOnClickListener {
                FirebaseDatabase.getInstance().getReference("Users/$userID").child("userName").setValue(name.text.toString())
                bottomSheetDialog.dismiss()
            }

        bottomSheetDialog.findViewById<TextView>(R.id.user_txtCancel)
            ?.setOnClickListener {
                bottomSheetDialog.dismiss()
            }
    }

    private fun updateABOUT() {
        val bottomSheetDialog = BottomSheetDialog(this, R.style.BottomSheetDialog)
        bottomSheetDialog.setContentView(R.layout.bsd_edit_about)
        bottomSheetDialog.show()

        val about = bottomSheetDialog.findViewById<EditText>(R.id.user_edtxtAbout)
        about!!.setText(binding.userTxtAbout.text.toString())

        bottomSheetDialog.findViewById<TextView>(R.id.user_txtSaveAbout)
            ?.setOnClickListener {
                FirebaseDatabase.getInstance().getReference("Users/$userID").child("about").setValue(about.text.toString())
                bottomSheetDialog.dismiss()
            }

        bottomSheetDialog.findViewById<TextView>(R.id.user_txtCancelAbout)
            ?.setOnClickListener {
                bottomSheetDialog.dismiss()
            }
    }

    private fun updateUI() {
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)   // all of the other activities on top of it will be closed
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)    // activity will become the start of a new task on this history stack
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)  // activity becomes the new root of an otherwise empty task, and any old activities are finished
        )
        finish()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        super.onBackPressed()
        updateUI()
    }

    override fun onResume() {
        super.onResume()
        //  IF USER IS ACTIVE ON APP, STORE IT AS ACTIVE USER
        FirebaseDatabase.getInstance().reference.child("Active Users").child(userID).setValue("Online")
    }

    @SuppressLint("SimpleDateFormat")
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        //  IF USER MINIMIZE APP, STORES THE LAST SEEN OF USER
        FirebaseDatabase.getInstance().reference.child("Active Users").child(userID)
            .setValue("Last seen at ${ SimpleDateFormat("hh:mm aa").format(Calendar.getInstance().time) }")
    }

}