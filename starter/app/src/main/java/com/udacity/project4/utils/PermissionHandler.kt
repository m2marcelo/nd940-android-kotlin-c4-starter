package com.udacity.project4.utils

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.annotation.MainThread
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.observe
import com.google.android.gms.maps.model.LatLng
import org.koin.core.context.GlobalContext
import java.util.*
import java.util.concurrent.Executors

open class PermissionHandler : Fragment()  {
    companion object {
        private val requestsList: Queue<PermissionRequest> = LinkedList()
        private val newRequest = MutableLiveData<Unit>()

        private const val REQUEST_CODE = 420

        @MainThread
        fun requestPermissions(
            vararg permissions: String,
            handler: (PermissionsResultEvent) -> Unit
        ) {
            requestsList.offer(PermissionRequest(permissions, handler))
            newRequest.postValue({}())
        }

        fun arePermissionsGranted(vararg permissions: String): Boolean = permissions.all {
            val context = GlobalContext.getOrNull()?.koin?.get<Application>() ?: return false
            ActivityCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

        private const val PROVIDER = LocationManager.GPS_PROVIDER
    }

    private var currentRequest: PermissionRequest? = null

    private val locationPermissions = arrayOf(
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_FINE_LOCATION
    )

    @SuppressLint("FragmentLiveDataObserve")
    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        getNextRequest()

        newRequest.observe(this) {
            getNextRequest()
        }
    }

    private fun getNextRequest() {
        currentRequest = requestsList.poll() ?: return
        requestPermissions(currentRequest!!.permissions, REQUEST_CODE)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        val failedPermissions = grantResults
            .filter { it == PackageManager.PERMISSION_DENIED }
            .mapIndexed { index, _ -> permissions[index] }

        val shouldShowRequestRationale =
            failedPermissions.any { shouldShowRequestPermissionRationale(it) }

        currentRequest?.handler?.invoke(
            PermissionsResultEvent(
                permissions,
                shouldShowRequestRationale,
                grantResults
            )
        )

        getNextRequest()
    }
}

data class PermissionsResultEvent(
    val permissions: Array<out String>,
    val shouldShowRequestRationale: Boolean,
    val grantResults: IntArray
) {
    val areAllGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PermissionsResultEvent

        if (!permissions.contentEquals(other.permissions)) return false
        if (shouldShowRequestRationale != other.shouldShowRequestRationale) return false
        if (!grantResults.contentEquals(other.grantResults)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = permissions.contentHashCode()
        result = 31 * result + shouldShowRequestRationale.hashCode()
        result = 31 * result + grantResults.contentHashCode()
        return result
    }
}

private data class PermissionRequest(
    val permissions: Array<out String>,
    val handler: (PermissionsResultEvent) -> Unit
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PermissionRequest

        if (!permissions.contentEquals(other.permissions)) return false
        if (handler != other.handler) return false

        return true
    }

    override fun hashCode(): Int {
        var result = permissions.contentHashCode()
        result = 31 * result + handler.hashCode()
        return result
    }
}