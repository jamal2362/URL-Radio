/*
 * EditStationDialog.kt
 * Implements the EditStationDialog class
 * A EditStationDialog shows an dialog in which the station properties can be changed
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
import android.widget.ImageView
import android.widget.TextView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.jamal2367.urlradio.R
import com.jamal2367.urlradio.core.Station
import com.jamal2367.urlradio.helpers.ImageHelper

/*
 * EditStationDialog class
 */
class EditStationDialog (private var editStationListener: EditStationListener) {

/* Interface used to communicate back to activity */
interface EditStationListener {
    fun onEditStationDialog(textInput: String, stationUuid: String, position: Int) {
    }
}


    /* Construct and show dialog */
    fun show(context: Context, station: Station, position: Int) {
        // prepare dialog builder
        val builder = MaterialAlertDialogBuilder(context, R.style.AlertDialogTheme)

        // get input field
        val inflater = LayoutInflater.from(context)
        val view = inflater.inflate(R.layout.dialog_edit_station, null)
        val inputField = view.findViewById<EditText>(R.id.edit_station_input_edit_text)
        val stationImageView = view.findViewById<ImageView>(R.id.station_image)

        // pre-fill with current track name
        inputField.setText(station.name, TextView.BufferType.EDITABLE)
        inputField.setSelection(station.name.length)
        inputField.inputType = InputType.TYPE_CLASS_TEXT

        // set station image
        stationImageView.setImageBitmap(ImageHelper.getStationImage(context, station.smallImage))
        stationImageView.contentDescription = "${context.getString(R.string.descr_player_station_image)}: ${station.name}"

        // set dialog view
        builder.setView(view)

        // set title
        builder.setTitle(R.string.dialog_edit_title)

        // add "add" button
        builder.setPositiveButton(R.string.dialog_button_save) { _, _ ->
            // hand text over to initiating activity
            inputField.text?.let {
                editStationListener.onEditStationDialog(it.toString(), station.uuid, position)
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
