package com.example.mapdemo;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;

import com.parse.LogInCallback;
import com.parse.ParseAnonymousUtils;
import com.parse.ParseException;
import com.parse.ParsePush;
import com.parse.ParseUser;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.location.Location;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import permissions.dispatcher.NeedsPermission;
import permissions.dispatcher.RuntimePermissions;

@RuntimePermissions
public class MapDemoActivity extends AppCompatActivity implements
		GoogleApiClient.ConnectionCallbacks,
		GoogleApiClient.OnConnectionFailedListener,
		LocationListener,
		GoogleMap.OnMapLongClickListener, GoogleMap.OnMarkerDragListener,
		MarkerUpdatesReceiver.PushInterface {

	private SupportMapFragment mapFragment;

	private GoogleMap map;

	private GoogleApiClient mGoogleApiClient;

	private LocationRequest mLocationRequest;

	private long UPDATE_INTERVAL = 60000;  /* 60 secs */

	private long FASTEST_INTERVAL = 5000; /* 5 secs */

	/*
	 * Define a request code to send to Google Play services This code is
	 * returned in Activity.onActivityResult
	 */
	private final static int CONNECTION_FAILURE_RESOLUTION_REQUEST = 9000;

	private final static String CHANNEL_NAME = "android-2016";

	PushUtil mPushUtil;
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.map_demo_activity);

		if (TextUtils.isEmpty(getResources().getString(R.string.google_maps_api_key))) {
			throw new IllegalStateException(
					"You forgot to supply a Google Maps API key");
		}

		mapFragment = ((SupportMapFragment) getSupportFragmentManager()
				.findFragmentById(R.id.map));
		if (mapFragment != null) {
			mapFragment.getMapAsync(new OnMapReadyCallback() {
				@Override
				public void onMapReady(GoogleMap map) {
					loadMap(map);
				}
			});
		} else {
			Toast.makeText(this, "Error - Map Fragment was null!!", Toast.LENGTH_SHORT)
					.show();
		}

		IntentFilter intentFilter = new IntentFilter("com.parse.push.intent.RECEIVE");
		registerReceiver(new MarkerUpdatesReceiver(this), intentFilter);

		mPushUtil = new PushUtil();

		if (ParseUser.getCurrentUser() != null) { // start with existing user
			startWithCurrentUser();
		} else { // If not logged in, login as a new anonymous user
			login();
		}

		ParsePush.subscribeInBackground(CHANNEL_NAME);
	}

	protected void loadMap(GoogleMap googleMap) {
		map = googleMap;
		if (map != null) {
			// Map is ready
			map.setOnMapLongClickListener(this);
			map.setOnMarkerDragListener(this);
			Toast.makeText(this, "Map Fragment was loaded properly!",
					Toast.LENGTH_SHORT).show();
			MapDemoActivityPermissionsDispatcher.getMyLocationWithCheck(this);
		} else {
			Toast.makeText(this, "Error - Map was null!!", Toast.LENGTH_SHORT).show();
		}
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, String[] permissions,
			int[] grantResults) {
		super.onRequestPermissionsResult(requestCode, permissions, grantResults);
		MapDemoActivityPermissionsDispatcher
				.onRequestPermissionsResult(this, requestCode, grantResults);
	}

	@SuppressWarnings("all")
	@NeedsPermission({Manifest.permission.ACCESS_FINE_LOCATION,
			Manifest.permission.ACCESS_COARSE_LOCATION})
	void getMyLocation() {
		if (map != null) {
			// Now that map has loaded, let's get our location!
			map.setMyLocationEnabled(true);
			mGoogleApiClient = new GoogleApiClient.Builder(this)
					.addApi(LocationServices.API)
					.addConnectionCallbacks(this)
					.addOnConnectionFailedListener(this).build();
			connectClient();
		}
	}

	protected void connectClient() {
		// Connect the client.
		if (isGooglePlayServicesAvailable() && mGoogleApiClient != null) {
			mGoogleApiClient.connect();
		}
	}

	/*
         * Called when the Activity becomes visible.
        */
	@Override
	protected void onStart() {
		super.onStart();
		connectClient();
	}

	/*
             * Called when the Activity is no longer visible.
             */
	@Override
	protected void onStop() {
		// Disconnecting the client invalidates it.
		if (mGoogleApiClient != null) {
			mGoogleApiClient.disconnect();
		}
		super.onStop();
	}

	/*
	 * Handle results returned to the FragmentActivity by Google Play services
	 */
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		// Decide what to do based on the original request code
		switch (requestCode) {

			case CONNECTION_FAILURE_RESOLUTION_REQUEST:
			/*
			 * If the result code is Activity.RESULT_OK, try to connect again
			 */
				switch (resultCode) {
					case Activity.RESULT_OK:
						mGoogleApiClient.connect();
						break;
				}

		}
	}

	private boolean isGooglePlayServicesAvailable() {
		// Check that Google Play services is available
		int resultCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(this);
		// If Google Play services is available
		if (ConnectionResult.SUCCESS == resultCode) {
			// In debug mode, log the status
			Log.d("Location Updates", "Google Play services is available.");
			return true;
		} else {
			// Get the error dialog from Google Play services
			Dialog errorDialog = GooglePlayServicesUtil.getErrorDialog(resultCode, this,
					CONNECTION_FAILURE_RESOLUTION_REQUEST);

			// If Google Play services can provide an error dialog
			if (errorDialog != null) {
				// Create a new DialogFragment for the error dialog
				ErrorDialogFragment errorFragment = new ErrorDialogFragment();
				errorFragment.setDialog(errorDialog);
				errorFragment.show(getSupportFragmentManager(), "Location Updates");
			}

			return false;
		}
	}

	/*
	 * Called by Location Services when the request to connect the client
	 * finishes successfully. At this point, you can request the current
	 * location or start periodic updates
	 */
	@Override
	public void onConnected(Bundle dataBundle) {
		// Display the connection status
		Location location = LocationServices.FusedLocationApi
				.getLastLocation(mGoogleApiClient);
		if (location != null) {
			Toast.makeText(this, "GPS location was found!", Toast.LENGTH_SHORT).show();
			LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
			CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLngZoom(latLng, 17);
			map.animateCamera(cameraUpdate);
		} else {
			Toast.makeText(this, "Current location was null, enable GPS on emulator!",
					Toast.LENGTH_SHORT).show();
		}
		startLocationUpdates();
	}

	protected void startLocationUpdates() {
		mLocationRequest = new LocationRequest();
		mLocationRequest.setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY);
		mLocationRequest.setInterval(UPDATE_INTERVAL);
		mLocationRequest.setFastestInterval(FASTEST_INTERVAL);
		LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient,
				mLocationRequest, this);
	}

	public void onLocationChanged(Location location) {
		// Report to the UI that the location was updated
		String msg = "Updated Location: " +
				Double.toString(location.getLatitude()) + "," +
				Double.toString(location.getLongitude());
		Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();

	}

	/*
         * Called by Location Services if the connection to the location client
         * drops because of an error.
         */
	@Override
	public void onConnectionSuspended(int i) {
		if (i == CAUSE_SERVICE_DISCONNECTED) {
			Toast.makeText(this, "Disconnected. Please re-connect.", Toast.LENGTH_SHORT)
					.show();
		} else if (i == CAUSE_NETWORK_LOST) {
			Toast.makeText(this, "Network lost. Please re-connect.", Toast.LENGTH_SHORT)
					.show();
		}
	}

	/*
	 * Called by Location Services if the attempt to Location Services fails.
	 */
	@Override
	public void onConnectionFailed(ConnectionResult connectionResult) {
		/*
		 * Google Play services can resolve some errors it detects. If the error
		 * has a resolution, try sending an Intent to start a Google Play
		 * services activity that can resolve error.
		 */
		if (connectionResult.hasResolution()) {
			try {
				// Start an Activity that tries to resolve the error
				connectionResult.startResolutionForResult(this,
						CONNECTION_FAILURE_RESOLUTION_REQUEST);
				/*
				 * Thrown if Google Play services canceled the original
				 * PendingIntent
				 */
			} catch (IntentSender.SendIntentException e) {
				// Log the error
				e.printStackTrace();
			}
		} else {
			Toast.makeText(getApplicationContext(),
					"Sorry. Location services not available to you",
					Toast.LENGTH_LONG).show();
		}
	}

	// Define a DialogFragment that displays the error dialog
	public static class ErrorDialogFragment extends DialogFragment {

		// Global field to contain the error dialog
		private Dialog mDialog;

		// Default constructor. Sets the dialog field to null
		public ErrorDialogFragment() {
			super();
			mDialog = null;
		}

		// Set the dialog to display
		public void setDialog(Dialog dialog) {
			mDialog = dialog;
		}

		// Return a Dialog to the DialogFragment.
		@Override
		public Dialog onCreateDialog(Bundle savedInstanceState) {
			return mDialog;
		}
	}

	@Override
	public void onMapLongClick(LatLng point) {
		showAlertDialogForPoint(point);

	}

	// Display the alert that adds the marker
	private void showAlertDialogForPoint(final LatLng point) {
		// inflate message_item.xml view
		View messageView = LayoutInflater.from(MapDemoActivity.this).
				inflate(R.layout.message_item, null);
		// Create alert dialog builder
		AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
		// set message_item.xml to AlertDialog builder
		alertDialogBuilder.setView(messageView);

		// Create alert dialog
		final AlertDialog alertDialog = alertDialogBuilder.create();

		// Configure dialog button (OK)
		alertDialog.setButton(DialogInterface.BUTTON_POSITIVE, "OK",
				new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						// Define color of marker icon
						BitmapDescriptor defaultMarker =
								BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN);
						// Extract content from alert dialog
						String title = ((EditText) alertDialog.findViewById(R.id.etTitle)).
								getText().toString();
						String snippet = ((EditText) alertDialog.findViewById(R.id.etSnippet)).
								getText().toString();

						BitmapDescriptor bitmapDescriptor = MapUtils.createBubble(MapDemoActivity.this, 0, title);

						Marker marker = MapUtils.addMarker(map, point, title, snippet, bitmapDescriptor);
						MapUtils.dropPinEffect(marker);
						marker.setDraggable(true);
						mPushUtil.sendPushNotification(marker, CHANNEL_NAME);
					}
				});

		// Configure dialog button (Cancel)
		alertDialog.setButton(DialogInterface.BUTTON_NEGATIVE, "Cancel",
				new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int id) { dialog.cancel(); }
				});

		// Display the dialog
		alertDialog.show();
	}

	@Override
	public void onMarkerDragStart(Marker marker) {

	}

	@Override
	public void onMarkerDrag(Marker marker) {

	}

	@Override
	public void onMarkerDragEnd(Marker marker) {
		mPushUtil.sendPushNotification(marker, "android-2016");
	}

	@Override
	public void onMarkerUpdate(PushRequest pushRequest) {
		mPushUtil.handleMarkerUpdates(this, pushRequest, map);
	}

	// Create an anonymous user using ParseAnonymousUtils and set sUserId
	void login() {
		ParseAnonymousUtils.logIn(new LogInCallback() {
			@Override
			public void done(ParseUser user, ParseException e) {
				if (e != null) {
					Log.e("test", "Anonymous login failed: ", e);
				} else {
					startWithCurrentUser();
				}
			}
		});
	}

	// Get the userId from the cached currentUser object
	void startWithCurrentUser() {
		// TODO:
	}


}