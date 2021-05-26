/*
 * RenameTrackDialog.kt
 * Implements the RenameTrackDialog class
 * A RenameTrackDialog shows an dialog in which the station name can be changed
 *
 * This file is part of
 * URL Radio - Radio App for Android
 *
 * Copyright (c) 2015-21 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */

package com.jamal2367.urlradio.dialogs

import android.content.Context
import android.text.InputType
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.TextView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.jamal2367.urlradio.R

/*
 * RenameStationDialog class
 */
class RenameStationDialog (private var renameStationListener: RenameStationListener) {

/* Interface used to communicate back to activity */
interface RenameStationListener {
    fun onRenameStationDialog(textInput: String, stationUuid: String, position: Int) {
    }
}


    /* Construct and show dialog */
    fun show(context: Context, stationName: String, stationUuid: String, position: Int) {
        // prepare dialog builder
        val builder = MaterialAlertDialogBuilder(context, R.style.AlertDialogTheme)

        // get input field
        val inflater = LayoutInflater.from(context)
        val view = inflater.inflate(R.layout.dialog_rename_station, null)
        val inputField = view.findViewById<EditText>(R.id.edit_station_input_edit_text)

        // pre-fill with current station name
        inputField.setText(stationName, TextView.BufferType.EDITABLE)
        inputField.setSelection(stationName.length)
        inputField.inputType = InputType.TYPE_CLASS_TEXT

        // set dialog view
        builder.setView(view)

        // add "add" button
        builder.setPositiveButton(R.string.dialog_button_rename) { _, _ ->
            // hand text over to initiating activity
            inputField.text?.let {
                var newStationName: String = it.toString()
                if (newStationName.isEmpty()) newStationName = stationName
                renameStationListener.onRenameStationDialog(newStationName, stationUuid, position)
            }
        }

        // add cancel button
        builder.setNegativeButton(R.string.dialog_generic_button_cancel) { _, _ ->
            // listen for click on cancel button
            // do nothing
        }

        // display add dialog
        builder.show()
    }

}
