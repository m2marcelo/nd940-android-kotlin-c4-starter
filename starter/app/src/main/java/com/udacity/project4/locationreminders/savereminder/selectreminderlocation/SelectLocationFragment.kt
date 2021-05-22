package com.udacity.project4.locationreminders.savereminder.selectreminderlocation

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.*
import androidx.databinding.DataBindingUtil
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.udacity.project4.R
import com.udacity.project4.base.BaseFragment
import com.udacity.project4.base.NavigationCommand
import com.udacity.project4.databinding.FragmentSelectLocationBinding
import com.udacity.project4.locationreminders.savereminder.SaveReminderViewModel
import com.udacity.project4.utils.*
import org.koin.android.ext.android.inject

class SelectLocationFragment : BaseFragment(), OnMapReadyCallback {
    override val baseViewModel: SaveReminderViewModel by inject()

    private lateinit var binding: FragmentSelectLocationBinding
    private lateinit var map: GoogleMap
    private lateinit var selectedLocationMarker: Marker
    private lateinit var placeOfInterest: PointOfInterest

    lateinit var thiscontext: Context

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        binding = DataBindingUtil.inflate(
            inflater, R.layout.fragment_select_location, container, false
        )

        binding.lifecycleOwner = this
        binding.onSaveButtonClicked = View.OnClickListener { onLocationSelected() }

        thiscontext = container!!.context

        setHasOptionsMenu(true)
        setDisplayHomeAsUpEnabled(true)

        loadGoogleMap()
//        TODO: add style to the map
//        TODO: put a marker to location that the user selected

        return binding.root
    }

    private fun loadGoogleMap() {
        val mapFragment = childFragmentManager
            .findFragmentByTag(getString(R.string.map_fragment)) as? SupportMapFragment
            ?: return

        mapFragment.getMapAsync(this)
    }

    private fun onLocationSelected() {
        //        TODO: When the user confirms on the selected location,
        //         send back the selected location details to the view model
        //         and navigate back to the previous fragment to save the reminder and add the geofence
        baseViewModel.setSelectedLocation(placeOfInterest)
        baseViewModel.navigationCommand.postValue(NavigationCommand.Back)
    }


    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.map_options, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        fun setMapType(mapType: Int): Boolean {
            map.mapType = mapType
            return true
        }

        return when (item.itemId) {
            R.id.normal_map -> setMapType(GoogleMap.MAP_TYPE_NORMAL)
            R.id.hybrid_map -> setMapType(GoogleMap.MAP_TYPE_HYBRID)
            R.id.terrain_map -> setMapType(GoogleMap.MAP_TYPE_TERRAIN)
            R.id.satellite_map -> setMapType(GoogleMap.MAP_TYPE_SATELLITE)
            else -> false
        }
    }

    override fun onMapReady(map: GoogleMap) {
        this.map = map
        val markerOptions = MarkerOptions()
            .position(map.cameraPosition.target)
            .title(getString(R.string.dropped_pin))
            .draggable(true)

        map.setMapStyle(
            MapStyleOptions.loadRawResourceStyle(
                requireContext(),
                R.raw.map_style
            )
        )

        selectedLocationMarker = map.addMarker(markerOptions)

        baseViewModel.selectedPlaceOfInterest.value.let {
            placeOfInterest = it ?: PointOfInterest(map.cameraPosition.target, null, null)

            if (it == null) {
                startAtCurrentLocation()
                Log.d("LOCATION", "CHAMANDO O STARTATCURRENTLOCATION")
            } else {
                Log.d("Location","TO NO ELSE " + it.latLng.latitude + "," + it.latLng.longitude)
                selectedLocationMarker.position = it.latLng
                putCameraOn(it.latLng)
            }

            Log.d("Location","depois do primeiro if " + it?.latLng?.latitude + "," + it?.latLng?.longitude)
        }



        map.setOnMapClickListener {
            selectedLocationMarker.position = it
            placeOfInterest = PointOfInterest(it, null, null)
        }

        map.setOnPoiClickListener {
            selectedLocationMarker.position = it.latLng
            placeOfInterest = it
        }
    }

    private fun locationPermissionHandler(event: PermissionsResultEvent, handler: () -> Unit) {
        if (event.areAllGranted) {
            handler()
            return
        }

        if (event.shouldShowRequestRationale) {
            baseViewModel.showSnackBar.postValue(getString(R.string.permission_denied_explanation))
        }
    }

    /*
    *
    * LocationHelper().startListeningUserLocation(this , object : LocationHelper.MyLocationListener {
            override fun onLocationChanged(location: Location) {
                // Here you got user location :)
                Log.d("Location","" + location.latitude + "," + location.longitude)
            }
        })
    * */
    @SuppressLint("MissingPermission")
    private fun startAtCurrentLocation() {
        LocationHelper().startListeningUserLocation( thiscontext, object : LocationHelper.MyLocationListener {
            override fun onLocationChanged(location: Location) {
                // Here you got user location :)
                Log.d("Location","" + location.latitude + "," + location.longitude)
            }
        })

        map.isMyLocationEnabled = true
    }


    private fun putCameraOn(latLng: LatLng) {
        val cameraPosition = CameraPosition.fromLatLngZoom(latLng, 18f)
        val cameraUpdate = CameraUpdateFactory.newCameraPosition(cameraPosition)

        map.animateCamera(cameraUpdate)
    }
}