package com.ipsmeet.chatapp.adapters

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.firebase.storage.FirebaseStorage
import com.ipsmeet.chatapp.R
import com.ipsmeet.chatapp.dataclasses.UserDataClass
import de.hdodenhof.circleimageview.CircleImageView
import java.io.File

class FoundUserAdapter(val context: Context, private val foundUser: UserDataClass, private val listener: OnClick)
    : RecyclerView.Adapter<FoundUserAdapter.UserViewHolder>() {

    class UserViewHolder(itemView: View): RecyclerView.ViewHolder(itemView) {
        val img: CircleImageView = itemView.findViewById(R.id.imgV_foundProfile)
        val name: TextView = itemView.findViewById(R.id.txt_foundUser)
        val number: TextView = itemView.findViewById(R.id.txt_found_number)
        val addFriend: ImageView = itemView.findViewById(R.id.add_found_user)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.layout_found_user, parent, false)
        return UserViewHolder(view)
    }

    override fun getItemCount(): Int {
        return 1
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        val localFile = File.createTempFile("tempFile", "jpeg")
        FirebaseStorage.getInstance().getReference("Images/*${ foundUser.key }").getFile(localFile)
            .addOnSuccessListener {
                val bitmap = BitmapFactory.decodeFile(localFile.absolutePath)
                Glide.with(context.applicationContext).load(bitmap).into(holder.img)
            }
            .addOnFailureListener {
                Log.d("Fail to load user profiles", it.message.toString())
            }

        holder.apply {
            name.text = foundUser.userName
            number.text = foundUser.phoneNumber

            addFriend.setOnClickListener {
                listener.clickListener(foundUser.key)
            }
        }
    }

    interface OnClick {
        fun clickListener(key: String)
    }
}