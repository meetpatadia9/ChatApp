package com.ipsmeet.chatapp.activities

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Toast
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import com.ipsmeet.chatapp.R
import com.ipsmeet.chatapp.databinding.ActivitySignInBinding
import dmax.dialog.SpotsDialog
import java.util.concurrent.TimeUnit

class SignInActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySignInBinding

    private lateinit var phoneNo: String
    private lateinit var auth: FirebaseAuth
    private lateinit var database: DatabaseReference

    private lateinit var progress: SpotsDialog
    lateinit var storedVerificationID:String
    lateinit var resendToken: PhoneAuthProvider.ForceResendingToken
    private lateinit var callbacks: PhoneAuthProvider.OnVerificationStateChangedCallbacks

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar!!.hide()
        binding = ActivitySignInBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        database = Firebase.database.reference

        progress = SpotsDialog(this, R.style.Custom)
        progress.setTitle("Please Wait!!")

        binding.btnSignIn.setOnClickListener {
            progress.show()
            startLogin(binding.signInPhoneNumber.text.toString())
        }

        callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                updateUI()
            }

            override fun onVerificationFailed(e: FirebaseException) {
                progress.dismiss()
                Toast.makeText(this@SignInActivity, e.message.toString(), Toast.LENGTH_SHORT).show()
            }

            override fun onCodeSent(verificationID: String, token: PhoneAuthProvider.ForceResendingToken) {
                super.onCodeSent(verificationID, token)

                storedVerificationID = verificationID
                resendToken = token

                startActivity(
                    Intent(this@SignInActivity, OTPActivity::class.java)
                        .putExtra("storedVerificationID", storedVerificationID)
                        .putExtra("phoneNumber", binding.signInPhoneNumber.text.toString())
                )
            }
        }
    }

    private fun startLogin(number: String) {
        if (number.isNotEmpty()) {
            phoneNo = "+91$number"
            sendOTP(phoneNo)
        }
        else {
            progress.dismiss()
            Toast.makeText(this, "Enter phone number!!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun sendOTP(number: String) {
        val option = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(number)
            .setTimeout(2L, TimeUnit.MINUTES)
            .setActivity(this)
            .setCallbacks(callbacks)
            .build()

        PhoneAuthProvider.verifyPhoneNumber(option)
    }

    override fun onStart() {
        super.onStart()
        val signedInUser = auth.currentUser
        if (signedInUser != null) {
            startActivity(
                Intent(this@SignInActivity, MainActivity::class.java)
            )
        }
    }

    private fun updateUI() {
        progress.dismiss()
        startActivity(
            Intent(this@SignInActivity, CreateProfileActivity::class.java)
        )
    }

}