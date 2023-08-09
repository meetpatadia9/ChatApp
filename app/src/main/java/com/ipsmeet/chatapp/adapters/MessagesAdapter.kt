package com.ipsmeet.chatapp.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import com.ipsmeet.chatapp.R
import com.ipsmeet.chatapp.dataclasses.MessagesDataClass

class MessagesAdapter(private val context: Context, private val messages: List<MessagesDataClass>, private val eventListener: MessageActionListener)
    : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var itemSend = 1
    private val itemReceive = 2

    class SenderViewHolder(itemView: View): RecyclerView.ViewHolder(itemView) {
        val txtMsg: TextView = itemView.findViewById(R.id.senderMsg)
        val sendTime: TextView = itemView.findViewById(R.id.senderTime)
        val sendImg: ImageView = itemView.findViewById(R.id.senderImg)
        val sendImgTime: TextView = itemView.findViewById(R.id.senderImgTime)
    }

    class ReceiverViewHolder(itemView: View): RecyclerView.ViewHolder(itemView) {
        val txtMsg: TextView = itemView.findViewById(R.id.receiverMsg)
        val receiverTime: TextView = itemView.findViewById(R.id.receiverTime)
        val receiverImg: ImageView = itemView.findViewById(R.id.receiverImg)
        val receiverImgTime: TextView = itemView.findViewById(R.id.receiverImgTime)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {    //  `viewType` is used for implementing different layouts
        return if (viewType == itemSend) {
            val view = LayoutInflater.from(context).inflate(R.layout.single_msg_sender, parent, false)
            SenderViewHolder(view)
        } else {
            val view = LayoutInflater.from(context).inflate(R.layout.single_msg_receiver, parent, false)
            ReceiverViewHolder(view)
        }
    }

    override fun getItemCount(): Int {
        return messages.size
    }

    override fun getItemViewType(position: Int): Int {
        return if (FirebaseAuth.getInstance().currentUser!!.uid == messages[position].senderID) {
            itemSend
        } else {
            itemReceive
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        //  SENDER
        if (holder.javaClass == SenderViewHolder::class.java) {
            val viewHolder: SenderViewHolder = holder as SenderViewHolder

            if (messages[position].message == "") {
                viewHolder.sendImg.visibility = View.VISIBLE
                viewHolder.sendImgTime.visibility = View.VISIBLE
                viewHolder.txtMsg.visibility = View.GONE
                viewHolder.sendTime.visibility = View.GONE

                FirebaseStorage.getInstance().getReference("Chat Media/${ messages[position].key }").downloadUrl
                    .addOnSuccessListener {
                        Glide.with(context).load(it).into(viewHolder.sendImg)
                    }

                viewHolder.sendImgTime.text = messages[position].timeStamp
            }
            else {
                viewHolder.sendImg.visibility = View.GONE
                viewHolder.sendImgTime.visibility = View.GONE
                viewHolder.txtMsg.visibility = View.VISIBLE
                viewHolder.sendTime.visibility = View.VISIBLE

                viewHolder.txtMsg.text = messages[position].message
                viewHolder.sendTime.text = messages[position].timeStamp
            }

            viewHolder.itemView.setOnLongClickListener {
                eventListener.longPressDelete(messages[position])
                true
            }
        }
        //  RECEIVER
        else {
            val viewHolder: ReceiverViewHolder = holder as ReceiverViewHolder

            if (messages[position].message == "") {
                viewHolder.receiverImg.visibility = View.VISIBLE
                viewHolder.receiverImgTime.visibility = View.VISIBLE
                viewHolder.txtMsg.visibility = View.GONE
                viewHolder.receiverTime.visibility = View.GONE

                FirebaseStorage.getInstance().getReference("Chat Media/${ messages[position].key }").downloadUrl
                    .addOnSuccessListener {
                        Glide.with(context).load(it).into(viewHolder.receiverImg)
                    }

                viewHolder.receiverImgTime.text = messages[position].timeStamp
            }
            else {
                viewHolder.receiverImg.visibility = View.GONE
                viewHolder.receiverImgTime.visibility = View.GONE
                viewHolder.txtMsg.visibility = View.VISIBLE
                viewHolder.receiverTime.visibility = View.VISIBLE

                viewHolder.txtMsg.text = messages[position].message
                viewHolder.receiverTime.text = messages[position].timeStamp
            }
        }


    }

    interface MessageActionListener {
        fun longPressDelete(senderID: MessagesDataClass)
    }
}