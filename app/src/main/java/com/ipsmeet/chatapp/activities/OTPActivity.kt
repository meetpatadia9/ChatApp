package com.ipsmeet.chatapp.activities

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthProvider
import com.ipsmeet.chatapp.R
import com.ipsmeet.chatapp.databinding.ActivityOtpBinding
import dmax.dialog.SpotsDialog

class OTPActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOtpBinding
    private lateinit var progress: SpotsDialog
    private lateinit var phoneNumber: String

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar!!.hide()
        binding = ActivityOtpBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        phoneNumber = intent.getStringExtra("phoneNumber").toString()   // Receiving `phoneNum` passed through Intent()

        progress = SpotsDialog(this, R.style.Custom)

        val storedVerificationID = intent.getStringExtra("storedVerificationID")

        binding.btnVerify.setOnClickListener {
            progress.show()

            val otp = binding.otpEnterOTP.text.toString()

            if (otp.isNotEmpty()) {
                val credential: PhoneAuthCredential = PhoneAuthProvider.getCredential(storedVerificationID.toString(), otp)

                auth.signInWithCredential(credential)
                    .addOnCompleteListener(this) {
                        if (it.isSuccessful) {
                            updateUI()
                        }
                        else {
                            Toast.makeText(this, "Invalid OTP", Toast.LENGTH_SHORT).show()
                        }
                    }
            }
        }

        binding.otpEnterOTP.addTextChangedListener(verifyOTP)
    }

    private val verifyOTP: TextWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            binding.btnVerify.isEnabled = false
        }

        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            binding.btnVerify.isEnabled = binding.otpEnterOTP.text.toString().trim().isNotEmpty()
        }

        override fun afterTextChanged(s: Editable?) {
            binding.btnVerify.isEnabled = binding.otpEnterOTP.text.toString().trim().isNotEmpty()
        }

    }

    private fun updateUI() {
        progress.dismiss()
        startActivity(
            Intent(this@OTPActivity, CreateProfileActivity::class.java)
                .putExtra("phoneNum", "+91$phoneNumber")
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)   // all of the other activities on top of it will be closed
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)    // activity will become the start of a new task on this history stack
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)  // activity becomes the new root of an otherwise empty task, and any old activities are finished
        )
        finish()
    }

}