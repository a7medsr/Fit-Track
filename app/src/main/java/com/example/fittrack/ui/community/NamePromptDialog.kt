package com.example.fittrack.ui.community

import android.app.Activity
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.example.fittrack.R

/**
 * Collects a first and last name from an account that has none.
 *
 * Sign-up asks for a name now, but accounts created before it did have nothing
 * to show other members. The alternatives are both bad -- publish the part of
 * their email before the @, which they never agreed to, or label every post
 * "Member" -- so it is asked for once, at the first moment it actually matters.
 */
object NamePromptDialog {

    fun show(activity: Activity, onSave: (String) -> Unit) {
        val view = activity.layoutInflater.inflate(R.layout.dialog_name, null)
        val dialog = AlertDialog.Builder(activity).setView(view).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val first = view.findViewById<EditText>(R.id.firstNameInput)
        val last = view.findViewById<EditText>(R.id.lastNameInput)
        val error = view.findViewById<TextView>(R.id.nameError)

        view.findViewById<View>(R.id.cancelBtn).setOnClickListener { dialog.dismiss() }
        view.findViewById<View>(R.id.saveBtn).setOnClickListener {
            val firstName = first.text.toString().trim()
            val lastName = last.text.toString().trim()

            if (firstName.isEmpty() || lastName.isEmpty()) {
                error.setText(R.string.community_name_prompt_error)
                error.visibility = View.VISIBLE
                return@setOnClickListener
            }

            dialog.dismiss()
            onSave("$firstName $lastName")
        }

        dialog.show()
    }
}
