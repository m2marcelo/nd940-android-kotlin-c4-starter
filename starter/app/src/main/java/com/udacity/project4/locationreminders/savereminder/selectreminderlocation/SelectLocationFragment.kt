package com.udacity.project4.locationreminders.savereminder.selectreminderlocation

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.*
import androidx.core.content.res.ResourcesCompat
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.observe
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.android.material.slider.Slider
import com.udacity.project4.R
import com.udacity.project4.base.BaseFragment
import com.udacity.project4.base.NavigationCommand
import com.udacity.project4.databinding.FragmentSelectLocationBinding
import com.udacity.project4.locationreminders.geofence.GeofenceConstants
import com.udacity.project4.locationreminders.savereminder.SaveReminderViewModel
import com.udacity.project4.utils.*
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

class SelectLocationFragment : BaseFragment(), OnMapReadyCallback {
    override val baseViewModel: SaveReminderViewModel by inject()
    private val selectLocationViewModel: SelectLocationViewModel by viewModel()

    private lateinit var binding: FragmentSelectLocationBinding
    private lateinit var map: GoogleMap
    private lateinit var selectedLocationMarker: Marker
    private lateinit var selectedLocationCircle: Circle

    lateinit var thiscontext: Context

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        binding = DataBindingUtil.inflate(
            inflater, R.layout.fragment_select_location, container, false
        )

        binding.lifecycleOwner = this
        binding.onSaveButtonClicked = View.OnClickListener { onLocationSelected() }
        binding.viewModel = selectLocationViewModel

        binding.radiusSlider.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {
            }

            override fun onStopTrackingTouch(slider: Slider) {
                selectLocationViewModel.closeRadiusSelector()
            }
        })

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

        selectLocationViewModel.radius.observe(viewLifecycleOwner) {
            if (!::selectedLocationCircle.isInitialized) {
                return@observe
            }

            selectedLocationCircle.radius =
                it?.toDouble() ?: GeofenceConstants.DEFAULT_RADIUS_IN_METERS.toDouble()
        }

        selectLocationViewModel.selectedLocation.observe(viewLifecycleOwner) {
            selectedLocationMarker.position = it.latLng
            selectedLocationCircle.center = it.latLng
            putCameraOn(it.latLng)
        }

        mapFragment.getMapAsync(this)
    }

    private fun onLocationSelected() {
        //        TODO: When the user confirms on the selected location,
        //         send back the selected location details to the view model
        //         and navigate back to the previous fragment to save the reminder and add the geofence
        selectLocationViewModel.closeRadiusSelector()
        baseViewModel.setSelectedLocation(selectLocationViewModel.selectedLocation.value!!)
        baseViewModel.setSelectedRadius(selectLocationViewModel.radius.value!!)
        baseViewModel.navigationCommand.postValue(NavigationCommand.Back)
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)
        selectLocationViewModel.closeRadiusSelector()
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

        val circleOptions = CircleOptions()
            .center(map.cameraPosition.target)
            .fillColor(ResourcesCompat.getColor(resources, R.color.map_radius_fill_color, null))
            .strokeColor(ResourcesCompat.getColor(resources, R.color.map_radius_stroke_color, null))
            .strokeWidth(4f)
            .radius(GeofenceConstants.DEFAULT_RADIUS_IN_METERS.toDouble())

        selectedLocationCircle = map.addCircle(circleOptions)


        baseViewModel.selectedPlaceOfInterest.value.let {
            selectLocationViewModel.setSelectedLocation(
                it ?: PointOfInterest(map.cameraPosition.target, null, null)
            )

            if (it == null) {
                startAtCurrentLocation()
            }
        }



        map.setOnMapClickListener {
            if (selectLocationViewModel.isRadiusSelectorOpen.value == true) {
                selectLocationViewModel.closeRadiusSelector()
            } else {
                selectLocationViewModel.setSelectedLocation(it)
            }
        }

        map.setOnPoiClickListener {
            if (selectLocationViewModel.isRadiusSelectorOpen.value == true) {
                selectLocationViewModel.closeRadiusSelector()
            } else {
                selectLocationViewModel.setSelectedLocation(it)
            }
        }

        map.setOnCameraMoveListener {
            selectLocationViewModel.zoomValue = map.cameraPosition.zoom
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

    @SuppressLint("MissingPermission")
    private fun startAtCurrentLocation() {
        if (!LocationHelper().hasLocationPermissions()) {
            LocationHelper().requestPermissions {
                locationPermissionHandler(it, this::startAtCurrentLocation)
            }

            return
        }

        LocationHelper().startListeningUserLocation( requireContext(), object : LocationHelper.MyLocationListener {
            override fun onLocationChanged(location: Location) {
                // Here you got user location :)
                Log.d("Location","" + location.latitude + "," + location.longitude)
            }
        })

        selectLocationViewModel.closeRadiusSelector()
        map.isMyLocationEnabled = true
    }


    private fun putCameraOn(latLng: LatLng) {
        val cameraPosition = CameraPosition.fromLatLngZoom(latLng, selectLocationViewModel.zoomValue)
        val cameraUpdate = CameraUpdateFactory.newCameraPosition(cameraPosition)

        map.animateCamera(cameraUpdate)
    }
}