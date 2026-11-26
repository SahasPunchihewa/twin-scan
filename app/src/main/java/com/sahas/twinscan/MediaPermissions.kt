package com.sahas.twinscan

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

enum class MediaAccess { NONE, PARTIAL, FULL }

object MediaPermissions {

    fun required(): Array<String> = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
            Manifest.permission.ACCESS_MEDIA_LOCATION,
        )

        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.ACCESS_MEDIA_LOCATION,
        )

        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.ACCESS_MEDIA_LOCATION,
        )

        Build.VERSION.SDK_INT == Build.VERSION_CODES.Q -> arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.ACCESS_MEDIA_LOCATION,
        )

        else -> arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
        )
    }

    fun currentAccess(context: Context): MediaAccess = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> when {
            context.isGranted(Manifest.permission.READ_MEDIA_IMAGES) ||
                context.isGranted(Manifest.permission.READ_MEDIA_VIDEO) -> MediaAccess.FULL

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
                context.isGranted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) ->
                MediaAccess.PARTIAL

            else -> MediaAccess.NONE
        }

        context.isGranted(Manifest.permission.READ_EXTERNAL_STORAGE) -> MediaAccess.FULL
        else -> MediaAccess.NONE
    }

    fun canReadOriginals(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            context.isGranted(Manifest.permission.ACCESS_MEDIA_LOCATION)

    private fun Context.isGranted(permission: String) =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
}
