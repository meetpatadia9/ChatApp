package com.ipsmeet.chatapp.adapters

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.storage.FirebaseStorage
import com.ipsmeet.chatapp.R
import com.ipsmeet.chatapp.dataclasses.StatusDataClass
import com.ipsmeet.chatapp.dataclasses.UserDataClass
import java.io.File

class ChatAdapter(private val context: Context, private val chatList: List<UserDataClass>, private val listener: OnClick)
    : RecyclerView.Adapter<ChatAdapter.ChatViewHolder>() {

    private val userID = FirebaseAuth.getInstance().currentUser!!.uid

    class ChatViewHolder(itemView: View): RecyclerView.ViewHolder(itemView) {
        val frameLayout: FrameLayout = itemView.findViewById(R.id.chat_profileFrame)
        val profileImg: ImageView = itemView.findViewById(R.id.chat_imgView)
        val online: ImageView = itemView.findViewById(R.id.imgV_isOnline)
        val name: TextView = itemView.findViewById(R.id.chat_personName)
        val lastMsg: TextView = itemView.findViewById(R.id.chat_lastMsg)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.single_view_chat, parent, false)
        return ChatViewHolder(view)
    }

    override fun getItemCount(): Int {
        return chatList.size
    }

    override fun onBindViewHolder(holder: ChatViewHolder, @SuppressLint("RecyclerView") position: Int) {
        val onlineUsers = arrayListOf<StatusDataClass>()
        val senderRoom = userID + chatList[position].key

        /*
            ONLINE INDICATOR (GREEN DOT)
        */
        //  while user is on MainActivity
        FirebaseDatabase.getInstance().getReference("Active Users")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        Log.d("users", snapshot.toString())
                        for (online in snapshot.children) {
                            onlineUsers.add(StatusDataClass(online.key.toString(), online.value.toString()))
                        }

                        Log.d("onlineUsers", onlineUsers.toString())

                        for (a in onlineUsers) {
                            if (a.key == chatList[position].key && a.status == "Online") {
                                holder.online.visibility = View.VISIBLE
                            }

                            if (a.key == chatList[position].key && a.status != "Online") {
                                holder.online.visibility = View.GONE
                            }
                        }
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.d("Failed to load active users", error.message)
                }
            })

        //  while user is in someone's chat
        FirebaseDatabase.getInstance().getReference("Chats/$senderRoom/Status/${ chatList[position].key }")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        if (snapshot.key == chatList[position].key && snapshot.value == "In Chat") {
                            holder.online.visibility = View.VISIBLE
                        }

                        if (snapshot.key == chatList[position].key && snapshot.value != "In Chat") {
                            holder.online.visibility = View.VISIBLE
                        }
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.d("Failed to load active users", error.message)
                }
            })

        /*
            SETTING VALUES TO RESPECTED VIEWS
        */
        holder.apply {
            //  user's profile image
            val localFile = File.createTempFile("tempFile", "jpeg")
            FirebaseStorage.getInstance()
                .getReference("Images/*${chatList[position].key}").getFile(localFile)
                .addOnSuccessListener {
                    val bitmap = BitmapFactory.decodeFile(localFile.absolutePath)
                    Glide.with(context.applicationContext).load(bitmap).into(profileImg)
                }
                .addOnFailureListener {
                    Log.d("Fail to load chat profiles", it.message.toString())
                }
            //  user's name
            name.text = chatList[position].userName

            //  ITEM-CLICK LISTENER
            itemView.setOnClickListener {
                listener.openChat(chatList[position].key, chatList[position].token)
            }

            //  VIEW PROFILE-IMAGE POPUP
            frameLayout.setOnClickListener {
                listener.viewProfilePopup(chatList[position].key, chatList[position].token)
            }
        }
    }

    interface OnClick {
        //  passing user's ID and TOKEN
        fun openChat(key: String, token: String)
        fun viewProfilePopup(key: String, token: String)
    }

}