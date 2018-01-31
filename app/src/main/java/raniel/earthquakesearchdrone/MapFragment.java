package raniel.earthquakesearchdrone;

import android.Manifest;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.places.Places;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.GoogleMapOptions;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;

import static android.content.Context.LAYOUT_INFLATER_SERVICE;

/**
 * Created by Raniel on 5/19/2017.
 */

public class MapFragment extends Fragment implements OnMapReadyCallback, GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, View.OnClickListener {

    private static final int PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 1;
    private static final String TAG = "Drone";
    private static final int DEFAULT_ZOOM = 19;
    private static int victim_number = 0;
    private static int point_number = 0;
    private static ArrayList<LatLng> point = new ArrayList<>();
    //private final LatLng mDefaultLocation = new LatLng(14.598918, 121.005397);
    private final LatLng mDefaultLocation = new LatLng(14.598228, 121.011736);
    ArrayList<Polyline> polylines = new ArrayList<>();
    MapTask task;
    FloatingActionButton send, set, takeoff, rtl, land, clear, settings;
    int droneAltitude = 1;
    double droneAirspeed = 1.0;
    ProgressDialog initialDialog;
    Socket socket;
    Marker droneMark;
    TextView txtLat, txtLng, txtAlt, txtVolts, txtCur, txtLevel, mode, armed, status;
    LatLng droneLocation;
    AlertDialog settingsDialog;
    private GoogleMap mMap;
    private boolean mLocationPermissionGranted;
    private Location mLastKnownLocation;
    private CameraPosition mCameraPosition;
    private GoogleApiClient mGoogleApiClient;
    private SupportMapFragment mapFragment;
    private String system_status = "";
    String server = Config.dev_server.getValue();


    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_map, container, false);

        LocationManager locationManager = (LocationManager) getActivity().getSystemService(Context.LOCATION_SERVICE);

        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            buildAlertMessageNoGps();
        }

        // Do other setup activities here too, as described elsewhere in this tutorial.

        // Build the Play services client for use by the Fused Location Provider and the Places API.
        // Use the addApi() method to request the Google Places API and the Fused Location Provider.

        send = (FloatingActionButton) view.findViewById(R.id.send);
        clear = (FloatingActionButton) view.findViewById(R.id.clear);
        set = (FloatingActionButton) view.findViewById(R.id.setRoute);
        takeoff = (FloatingActionButton) view.findViewById(R.id.takeoff);
        rtl = (FloatingActionButton) view.findViewById(R.id.rtl);
        land = (FloatingActionButton) view.findViewById(R.id.land);
        settings = (FloatingActionButton) view.findViewById(R.id.settings);

        createSettingsModal();

        send.setOnClickListener(this);
        clear.setOnClickListener(this);
        set.setOnClickListener(this);
        rtl.setOnClickListener(this);
        land.setOnClickListener(this);
        takeoff.setOnClickListener(this);
        settings.setOnClickListener(this);

        mGoogleApiClient = new GoogleApiClient.Builder(getContext())
                .enableAutoManage(getActivity() /* FragmentActivity */,
                        this /* OnConnectionFailedListener */)
                .addConnectionCallbacks(this)
                .addApi(LocationServices.API)
                .addApi(Places.GEO_DATA_API)
                .addApi(Places.PLACE_DETECTION_API)
                .build();
        mGoogleApiClient.connect();

        try {
            socket = IO.socket(server);
            setupDrone();
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }

        return view;
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {

        try {
            mapFragment = (SupportMapFragment) getChildFragmentManager().findFragmentById(R.id.mapFragment);
            mapFragment.getMapAsync(MapFragment.this);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        // Do other setup activities here too, as described elsewhere in this tutorial.

        // Turn on the My Location layer and the related control on the map.
        updateLocationUI();

        // Get the current location of the device and set the position of the map.
        getDeviceLocation();

        //getDroneLocation();
        mMap.setOnMapClickListener(null);

        droneMark = mMap.addMarker(new MarkerOptions()
                .icon(BitmapDescriptorFactory.fromResource(R.drawable.red_arrow))
                //.icon(mMap.)
                .flat(true)
                .anchor(0.5f, 0.5f)
                .zIndex(1.0f)
                .position(mDefaultLocation));


    }

    private void getDeviceLocation() {
    /*
     * Before getting the device location, you must check location
     * permission, as described earlier in the tutorial. Then:
     * Get the best and most recent location of the device, which may be
     * null in rare cases when a location is not available.
     */

        if (mLocationPermissionGranted) {
            if (ActivityCompat.checkSelfPermission(getContext(),
                    Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                    && ActivityCompat.checkSelfPermission(getContext(),
                    Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

                ActivityCompat.requestPermissions(getActivity(), new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
                //return;
            }


            mLastKnownLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
        }

        // Set the map's camera position to the current location of the device.
        if (mCameraPosition != null) {
            mMap.moveCamera(CameraUpdateFactory.newCameraPosition(mCameraPosition));
        } else if (mLastKnownLocation != null) {
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(
                    new LatLng(mLastKnownLocation.getLatitude(),
                            mLastKnownLocation.getLongitude()), DEFAULT_ZOOM));
        } else {
            Log.d(TAG, "Current location is null. Using defaults.");
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(mDefaultLocation, DEFAULT_ZOOM));
            mMap.getUiSettings().setMyLocationButtonEnabled(false);
        }
    }

    /**
     * Handles the result of the request for location permissions.
     */
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String permissions[],
                                           @NonNull int[] grantResults) {
        mLocationPermissionGranted = false;
        switch (requestCode) {
            case PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    mLocationPermissionGranted = true;
                }
            }
        }
        updateLocationUI();
    }

    /**
     * Updates the map's UI settings based on whether the user has granted location permission.
     */
    private void updateLocationUI() {
        if (mMap == null) {
            return;
        }

        /*
         * Request location permission, so that we can get the location of the
         * device. The result of the permission request is handled by a callback,
         * onRequestPermissionsResult.
         */
        if (ContextCompat.checkSelfPermission(getContext(),
                Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            mLocationPermissionGranted = true;
        } else {
            ActivityCompat.requestPermissions(getActivity(),
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION);
        }

        if (mLocationPermissionGranted) {
            mMap.setMyLocationEnabled(true);
            mMap.getUiSettings().setMyLocationButtonEnabled(true);
        } else {
            mMap.setMyLocationEnabled(false);
            mMap.getUiSettings().setMyLocationButtonEnabled(false);
            mLastKnownLocation = null;
        }
    }

    private void buildAlertMessageNoGps() {
        final AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setMessage("Your GPS seems to be disabled, do you want to enable it?")
                .setCancelable(false)
                .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    public void onClick(@SuppressWarnings("unused") final DialogInterface dialog, @SuppressWarnings("unused") final int id) {
                        startActivity(new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                    }
                })
                .setNegativeButton("No", new DialogInterface.OnClickListener() {
                    public void onClick(final DialogInterface dialog, @SuppressWarnings("unused") final int id) {
                        dialog.cancel();
                    }
                });
        final AlertDialog alert = builder.create();
        alert.show();
    }

    public void setupDrone() {
        initialDialog = new ProgressDialog(getContext());
        initialDialog.setMessage("Waiting for vehicle to initialise. Please wait...");
        initialDialog.setCanceledOnTouchOutside(false);
        initialDialog.show();

        task = new MapTask(this);
        task.execute("getDroneParams");

    }

    public void setupSocketListeners() {

        Log.d("Drone", "setupSocketListeners");

        socket.on(Socket.EVENT_CONNECT, new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                Log.d("Drone", "Connected");
            }
        }).on(Socket.EVENT_DISCONNECT, new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                Log.d("Drone", "Disconnected");
            }
        }).on("parameters", new Emitter.Listener() {
            @Override
            public void call(final Object... args) {
                Log.d("Drone", "Received message");

                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        decodeJSON(args[0].toString());
                    }
                });
            }
        }).on("gas_detected", new Emitter.Listener() {
            @Override
            public void call(final Object... args) {
                Log.i("Drone", "Gas Detected!");
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        setGasDetectedLocation(args[0].toString());
                    }
                });

            }
        }).on("sound_detected", new Emitter.Listener() {
            @Override
            public void call(final Object... args) {
                Log.i("Drone", "Gas Detected!");
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        setSoundDetectedLocation(args[0].toString());
                    }
                });
            }
        });

    }

    public void decodeJSON(String json) {

        JSONObject reader, latlongalt, battery;
        LatLng droneLatLng;

        try {
            reader = new JSONObject(json);
            latlongalt = reader.getJSONObject("gps_location");
            battery = reader.getJSONObject("battery");

            droneMark.setRotation((float) reader.getDouble("heading"));
            droneMark.setPosition(new LatLng(latlongalt.getDouble("latitude"), latlongalt.getDouble("longitude")));

            droneLatLng = new LatLng(latlongalt.getDouble("latitude"), latlongalt.getDouble("longitude"));

            if (droneLocation == null)
                droneLocation = droneLatLng;
            else
                droneMark.setPosition(droneLatLng);

            txtLat.setText("LAT: " + droneLatLng.latitude);
            txtLng.setText("LNG: " + droneLatLng.longitude);

            system_status = reader.getString("system status");

            txtAlt.setText("ALT: " + (float) latlongalt.getDouble("altitude"));
            txtVolts.setText("Voltage: " + battery.getDouble("voltage") + "V");
            txtCur.setText("Current: " + battery.getDouble("current") + "A");
            txtLevel.setText("Level: " + battery.getDouble("level") + "%");
            mode.setText("Mode: " + reader.getString("mode"));
            armed.setText("Is armed?: " + reader.getBoolean("armed"));
            status.setText("System Status: " + system_status);

            if (system_status.equals("ACTIVE")) {
                takeoff.setVisibility(View.INVISIBLE);
                land.setVisibility(View.VISIBLE);
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(droneLatLng, DEFAULT_ZOOM));
            }

        } catch (JSONException e) {
            e.printStackTrace();
        }

    }


    private void setGasDetectedLocation(String json) {
        try {
            JSONObject sensor_json = new JSONObject(json);
            LatLng gas_detected_location = new LatLng(sensor_json.getDouble("latitude"),
                    sensor_json.getDouble("longitude"));
            mMap.addMarker(new MarkerOptions()
                    .icon(BitmapDescriptorFactory.fromResource(R.drawable.mask))
                    .flat(true)
                    .anchor(0.5f, 0.5f)
                    .position(gas_detected_location));
        } catch (JSONException e) {
            e.printStackTrace();
        }

    }

    private void setSoundDetectedLocation(String json) {
        try {
            JSONObject sensor_json = new JSONObject(json);
            LatLng sound_detected_location = new LatLng(sensor_json.getDouble("latitude"),
                    sensor_json.getDouble("longitude"));
            mMap.addMarker(new MarkerOptions()
                    .icon(BitmapDescriptorFactory.fromResource(R.drawable.alert))
                    .flat(true)
                    .anchor(0.5f, 0.5f)
                    .position(sound_detected_location));
        } catch (JSONException e) {
            e.printStackTrace();
        }

    }

    public void setRoute(View view) {

        send.setVisibility(View.VISIBLE);
        clear.setVisibility(View.VISIBLE);
        set.setVisibility(View.INVISIBLE);
        rtl.setVisibility(View.INVISIBLE);
        land.setVisibility(View.INVISIBLE);
        takeoff.setVisibility(View.INVISIBLE);
        settings.setVisibility(View.INVISIBLE);

        mMap.setOnMapClickListener(new GoogleMap.OnMapClickListener() {
            @Override
            public void onMapClick(LatLng latLng) {
                point.add(0, droneLocation);
                point.add(point.size() - 1, latLng);
                mMap.addMarker(new MarkerOptions()
                        .position(latLng)
                        .draggable(true)
                        .title("Point " + point.lastIndexOf(latLng)));
                getRoute();

            }
        });

    }

    private void getRoute() {

        if (polylines.size() > 0) {
            polylines.get(0).remove();
            polylines.clear();
        }

        Polyline rectLine = this.mMap.addPolyline(new PolylineOptions().width(5).color(Color.BLUE));
        polylines.add(rectLine);
        rectLine.setPoints(point);

    }

    public void returnToLand(View view) {
        //socket.emit("rtl", "RTL");

        if (system_status.equals("ACTIVE")) {
            task = new MapTask(this);
            task.execute("rtl");
        } else {
            Toast.makeText(getContext(), "Error. Drone is not active", Toast.LENGTH_LONG).show();
        }

    }

    public void landNow(View view) {
        //socket.emit("land", "Land");

        task = new MapTask(this);
        task.execute("land");

    }

    public void throttle(View view) {

        task = new MapTask(this);
        task.execute("takeoff");

        /*
        String json = "";
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.accumulate("altitude", droneAltitude);
            jsonObject.accumulate("airspeed", droneAirspeed);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        json = jsonObject.toString();
        socket.emit("takeoff", json);
        */

    }

    public void sendRoute(View view) {
        task = new MapTask(this);
        task.execute("sendRoute");
        /*
        String json = "";
        JSONObject jsonObject = new JSONObject();
        JSONArray jsonArrayLatLong = new JSONArray();

        try {
            for (int i = 1; i < point.size()-1; i++) {
                JSONObject jsonLatLong = new JSONObject();
                jsonLatLong.accumulate("latitude", point.get(i).latitude);
                jsonLatLong.accumulate("longitude", point.get(i).longitude);
                jsonArrayLatLong.put(jsonLatLong);
            }
            jsonObject.accumulate("points", jsonArrayLatLong);
            jsonObject.accumulate("altitude", droneAltitude);
            jsonObject.accumulate("airspeed", droneAirspeed);

        } catch (JSONException e) {
            e.printStackTrace();
        }
        json = jsonObject.toString();
        socket.emit("route", json);
        */

        send.setVisibility(View.INVISIBLE);
        clear.setVisibility(View.INVISIBLE);
        set.setVisibility(View.VISIBLE);
        rtl.setVisibility(View.VISIBLE);
        land.setVisibility(View.VISIBLE);
        takeoff.setVisibility(View.VISIBLE);
        settings.setVisibility(View.VISIBLE);

        mMap.setOnMapClickListener(null);
    }

    public void clearRoute(View view) {
        polylines.clear();
        mMap.clear();
    }

    private void setVictimPosition(double latitude, double longitude) {
        LatLng pos = new LatLng(latitude, longitude);
        ++victim_number;
        mMap.addMarker(new MarkerOptions().position(pos).title("Victim " + victim_number));
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

    }

    @Override
    public void onClick(View v) {

        if (v.getId() == send.getId()) {
            sendRoute(v);
        } else if (v.getId() == set.getId()) {
            setRoute(v);
        } else if (v.getId() == clear.getId()) {
            clearRoute(v);
        } else if (v.getId() == rtl.getId()) {
            returnToLand(v);
        } else if (v.getId() == land.getId()) {
            landNow(v);
            takeoff.setVisibility(View.VISIBLE);
            land.setVisibility(View.INVISIBLE);
        } else if (v.getId() == takeoff.getId()) {
            throttle(v);
            takeoff.setVisibility(View.INVISIBLE);
            land.setVisibility(View.VISIBLE);
        } else {
            showSettings(v);
        }
    }

    private void createSettingsModal() {

        LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(LAYOUT_INFLATER_SERVICE);
        View layout = inflater.inflate(R.layout.settings_modal, null);

        txtLat = (TextView) layout.findViewById(R.id.lat);
        txtLng = (TextView) layout.findViewById(R.id.lng);
        txtAlt = (TextView) layout.findViewById(R.id.alt);
        txtVolts = (TextView) layout.findViewById(R.id.voltage);
        txtCur = (TextView) layout.findViewById(R.id.current);
        txtLevel = (TextView) layout.findViewById(R.id.level);
        mode = (TextView) layout.findViewById(R.id.mode);
        armed = (TextView) layout.findViewById(R.id.armed);
        status = (TextView) layout.findViewById(R.id.status);

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setView(layout);

        final TextView altitude = (TextView) layout.findViewById(R.id.altitude);
        final TextView airspeed = (TextView) layout.findViewById(R.id.airspeed);
        final SeekBar seekAltitude = (SeekBar) layout.findViewById(R.id.seekBar1);
        final SeekBar seekAirspeed = (SeekBar) layout.findViewById(R.id.seekBar2);
        seekAltitude.setProgress(droneAltitude);
        seekAirspeed.setProgress((int) droneAirspeed);
        droneAirspeed /= 2;
        altitude.setText("Set Altitude: " + droneAltitude + " m");
        airspeed.setText("Airspeed: " + droneAirspeed + " m/s");

        seekAltitude.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {

            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                altitude.setText("Altitude: " + progress + " m");
                droneAltitude = progress;
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        seekAirspeed.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {

            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                airspeed.setText("Airspeed: " + progress * 0.5 + " m/s");
                droneAirspeed = progress * 0.5;
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        builder.setPositiveButton("OK",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });

        builder.setTitle("Settings");

        settingsDialog = builder.create();

    }

    public void showSettings(View v) {

        /*
        LayoutInflater inflater = (LayoutInflater)getContext().getSystemService(LAYOUT_INFLATER_SERVICE);
        View layout = inflater.inflate(R.layout.settings_modal, (ViewGroup) v.findViewById(R.id.mapFragment));

        */
        settingsDialog.show();

    }


    @Override
    public void onDestroyView() {
        super.onDestroyView();
        socket.disconnect();
        // socket.close();
    }

    public static class MapTask extends AsyncTask<String, Integer, String> {

        private WeakReference<MapFragment> activityReference;
        private String command = "";
        private MapFragment activity;

        public MapTask(MapFragment context) {

            this.activityReference = new WeakReference<>(context);

            // get a reference to the activity if it is still there
            activity = activityReference.get();
        }

        @Override
        protected String doInBackground(String... params) {

            if (activity == null) return null;

            command = params[0];

            String json = "";
            String server = activity.server;

            URL url = null;
            JSONObject jsonObject = new JSONObject();

            if (command.equals("getDroneParams")) {

                try {

                    url = new URL(server + "/params");
                    HttpURLConnection httpURLConnection = (HttpURLConnection) url.openConnection();
                    httpURLConnection.setRequestMethod("GET");
                    int responseCode = httpURLConnection.getResponseCode();

                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        BufferedReader in = new BufferedReader(new InputStreamReader(
                                httpURLConnection.getInputStream()));
                        String inputLine;
                        StringBuffer response = new StringBuffer();

                        while ((inputLine = in.readLine()) != null) {
                            response.append(inputLine).append("\n");
                        }
                        in.close();

                        return response.toString();
                    } else {
                        Log.d("Drone", "GET request not worked");
                    }

                } catch (IOException e) {
                    e.printStackTrace();
                }

            } else {

                try {

                    int droneAltitude = activity.droneAltitude;
                    double droneAirspeed = activity.droneAirspeed;

                    if (command.equals("sendRoute")) {
                        JSONArray jsonArrayLatLong = new JSONArray();
                        for (int i = 1; i < point.size() - 1; i++) {
                            JSONObject jsonLatLong = new JSONObject();
                            jsonLatLong.accumulate("latitude", point.get(i).latitude);
                            jsonLatLong.accumulate("longitude", point.get(i).longitude);
                            jsonArrayLatLong.put(jsonLatLong);
                        }

                        JSONObject jsonLatLong = new JSONObject();
                        jsonLatLong.accumulate("latitude", point.get(0).latitude);
                        jsonLatLong.accumulate("longitude", point.get(0).longitude);
                        jsonArrayLatLong.put(jsonLatLong);

                        jsonObject.accumulate("points", jsonArrayLatLong);
                        jsonObject.accumulate("altitude", droneAltitude);
                        jsonObject.accumulate("airspeed", droneAirspeed);
                        url = new URL(server + "/route");
                        json = jsonObject.toString();
                    } else if (command.equals("takeoff")) {
                        url = new URL(server + "/takeoff");
                        jsonObject.accumulate("altitude", droneAltitude);
                        jsonObject.accumulate("airspeed", droneAirspeed);
                        json = jsonObject.toString();
                    } else if (command.equals("rtl")) {
                        url = new URL(server + "/rtl");
                        json = "rtl";
                    } else if (command.equals("land")) {
                        url = new URL(server + "/land");
                        json = "land";
                    }

                    HttpURLConnection httpURLConnection = (HttpURLConnection) url.openConnection();
                    httpURLConnection.setRequestMethod("POST");
                    httpURLConnection.setDoOutput(true);
                    httpURLConnection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                    OutputStream outputStream = httpURLConnection.getOutputStream();
                    outputStream.write(json.getBytes());
                    outputStream.flush();
                    outputStream.close();
                    InputStream inputStream = httpURLConnection.getInputStream();
                    BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream, "iso-8859-1"));
                    String result = "";
                    String line = "";
                    while ((line = bufferedReader.readLine()) != null) {
                        result += line;
                    }
                    bufferedReader.close();
                    inputStream.close();
                    httpURLConnection.disconnect();

                } catch (JSONException e) {
                    e.printStackTrace();

                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            return json;

        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        @Override
        protected void onPostExecute(final String s) {
            super.onPostExecute(s);

            if (s == null || s.equals("Error!")) {
                activity.initialDialog.setMessage("Cannot initialise the vehicle");
                activity.initialDialog.setCanceledOnTouchOutside(true);
                return;
            }

            if (command.equals("getDroneParams")) {

                activity.initialDialog.dismiss();
                activity.setupSocketListeners();
                activity.socket.connect();

                if (activity.droneMark == null) {
                    Handler handler = new Handler();
                    handler.postDelayed(new Runnable() {
                        public void run() {
                            activity.decodeJSON(s);
                        }
                    }, 2000);
                    return;
                }

                activity.decodeJSON(s);

            }
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            super.onProgressUpdate(values);
            activity.initialDialog.show();
        }

    }

}
