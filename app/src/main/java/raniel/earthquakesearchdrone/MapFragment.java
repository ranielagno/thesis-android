package raniel.earthquakesearchdrone;

import android.*;
import android.Manifest;
import android.app.AlertDialog;
import android.app.Dialog;
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
import org.w3c.dom.Text;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;

import io.socket.client.Ack;
import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;

import static android.content.Context.LAYOUT_INFLATER_SERVICE;

/**
 * Created by Raniel on 5/19/2017.
 */

public class MapFragment extends Fragment implements OnMapReadyCallback, GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, View.OnClickListener{

    private GoogleMap mMap;
    private boolean mLocationPermissionGranted;
    private static final int PERMISSIONS_REQUEST_ACCESS_COARSE_LOCATION = 1;
    private Location mLastKnownLocation;
    private static final String TAG = "Drone";
    private static final int DEFAULT_ZOOM = 19;
    private final LatLng mDefaultLocation = new LatLng(14.598918, 121.005397);
    private CameraPosition mCameraPosition;
    private GoogleApiClient mGoogleApiClient;

    private static int victim_number = 0;
    private static int point_number = 0;
    private LatLng startingPoint;
    private static ArrayList<LatLng> point;
    ArrayList<Polyline> polylines = new ArrayList<Polyline>();
    MapTask task;
    FloatingActionButton send, set, takeoff, rtl, land, clear, settings;

    int droneAltitude = 0;
    int droneAirspeed = 0;

    ProgressDialog initialDialog;
    Socket socket;
    String server = "http://earthquakesearchdrone.herokuapp.com/drone";

    Marker droneMark;

    static TextView txtLat, txtLng, txtAlt, txtVolts, txtCur, txtLevel, mode, armed, status;

    static LatLng droneLocation;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_map, container, false);

        point = new ArrayList<>();

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

        send.setOnClickListener(this);
        clear.setOnClickListener(this);
        set.setOnClickListener(this);
        rtl.setOnClickListener(this);
        land.setOnClickListener(this);
        takeoff.setOnClickListener(this);
        settings.setOnClickListener(this);

        initialDialog = new ProgressDialog(getContext());
        initialDialog.setMessage("Waiting for vehicle to initialise. Please wait...");
        initialDialog.setCanceledOnTouchOutside(false);

        mGoogleApiClient = new GoogleApiClient.Builder(getContext())
                .enableAutoManage(getActivity() /* FragmentActivity */,
                        this /* OnConnectionFailedListener */)
                .addConnectionCallbacks(this)
                .addApi(LocationServices.API)
                .addApi(Places.GEO_DATA_API)
                .addApi(Places.PLACE_DETECTION_API)
                .build();
        mGoogleApiClient.connect();

        return view;
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {

        try{
            SupportMapFragment mapFragment = (SupportMapFragment) getChildFragmentManager().findFragmentById(R.id.mapFragment);
            mapFragment.getMapAsync(MapFragment.this);
         }catch (Exception e){
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

        try {
            socket = IO.socket(server);
            socket.on(Socket.EVENT_CONNECT, new Emitter.Listener() {
                @Override
                public void call(Object... args) {
                    Log.d("Drone", "Connected");
                }
            }).on("paramaters", new Emitter.Listener() {
                @Override
                public void call(final Object... args) {
                    Log.d("Drone", args[0].toString());
                    if(initialDialog.isShowing())
                        initialDialog.dismiss();

                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if(!args[0].toString().equals("parameters"))
                                decodeJSON(args[0].toString());
                        }
                    });
                }
            });
            socket.connect();
            initialDialog.show();
            socket.emit("setup", "Setup", new Ack() {
                @Override
                public void call(Object... args) {
                    Log.d("Drone", "Setup command sent");
                }
            });

        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
        /*
        MapTask task = new MapTask();
        task.execute("getDroneParams");
        */
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
                    Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
                    && ActivityCompat.checkSelfPermission(getContext(),
                    android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return;
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
            case PERMISSIONS_REQUEST_ACCESS_COARSE_LOCATION: {
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
                Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            mLocationPermissionGranted = true;
        } else {
            ActivityCompat.requestPermissions(getActivity(),
                    new String[]{android.Manifest.permission.ACCESS_COARSE_LOCATION},
                    PERMISSIONS_REQUEST_ACCESS_COARSE_LOCATION);
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

    private void decodeJSON(String json){
        JSONObject reader, latlongalt, battery;
        try {
            reader = new JSONObject(json);
            latlongalt = reader.getJSONObject("gps_location");
            battery = reader.getJSONObject("battery");
            droneLocation = new LatLng(
                    14.605160,
                    121.002181
                    //latlongalt.getDouble("latitude"),
                    //latlongalt.getDouble("longitude")
                    );
            setDroneParams(
                    droneLocation,
                    (float)latlongalt.getDouble("altitude"),
                    (float)reader.getDouble("heading"),
                    battery.getDouble("voltage"),
                    battery.getDouble("current"),
                    battery.getDouble("level"),
                    reader.getString("mode"),
                    reader.getBoolean("armed"),
                    reader.getString("system status")
            );


        } catch (JSONException e){
            e.printStackTrace();
        }

    }

    private void setDroneParams(LatLng latLng, float altitude, float rotation,
                                double voltage, double current, double level, String modeString,
                                boolean is_armed, String system_status){

        if(droneMark == null) {
            droneMark = mMap.addMarker(new MarkerOptions()
                    .icon(BitmapDescriptorFactory.fromResource(R.drawable.red_arrow))
                    .position(latLng)
                    .flat(true)
                    .rotation(rotation)
                    .anchor(0.5f, 0.5f));
        }else{
            droneMark.setPosition(latLng);
            droneMark.setRotation(rotation);
        }
        if(txtLat != null && txtLng != null && txtAlt != null && txtVolts != null && txtCur != null
                && txtLevel != null && mode != null && armed != null && status != null){

            txtLat.setText("LAT: "+latLng.latitude);
            txtLng.setText("LNG: "+latLng.longitude);
            txtAlt.setText("ALT: "+altitude);
            txtVolts.setText("Voltage: "+voltage+"V");
            txtCur.setText("Current: "+current+"A");
            txtLevel.setText("Level: "+level+"%");
            mode.setText("Mode: "+modeString);
            armed.setText("Is armed?: "+is_armed);
            status.setText("System Status: "+system_status);
        }

    }

    public void setRoute(View view){

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
                point.add(0,droneLocation);
                point.add(point.size()-1,latLng);
                mMap.addMarker(new MarkerOptions()
                        .position(latLng)
                        .title("Point "+point.lastIndexOf(latLng)));
                getRoute();

            }
        });

    }

    private void getRoute(){

        if(polylines.size() > 0){
            polylines.get(0).remove();
            polylines.clear();
        }

        Polyline rectLine = this.mMap.addPolyline(new PolylineOptions().width(5).color(Color.BLUE));
        polylines.add(rectLine);
        rectLine.setPoints(point);

    }

    public void returnToLand(View view){
        //socket.emit("rtl", "RTL");

        task = new MapTask();
        task.execute("rtl");

    }

    public void landNow(View view){
        //socket.emit("land", "Land");

        task = new MapTask();
        task.execute("land");

    }

    public void throttle(View view) {
        /*
        task = new MapTask();
        task.execute("takeoff");
        */

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

    }

    public void sendRoute(View view){
        task = new MapTask();
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

    public void clearRoute(View view){
        polylines.clear();
        mMap.clear();
    }

    private void setVictimPosition(double latitude, double longitude){
        LatLng pos = new LatLng(latitude, longitude);
        ++victim_number;
        mMap.addMarker(new MarkerOptions().position(pos).title("Victim "+victim_number));
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

    }

    @Override
    public void onClick(View v) {

         if(v.getId() == send.getId()){
             sendRoute(v);
         }else if(v.getId() == set.getId()){
             setRoute(v);
         }else if(v.getId() == clear.getId()){
             clearRoute(v);
         }else if(v.getId() == rtl.getId()){
             returnToLand(v);
         }else if(v.getId() == land.getId()){
             landNow(v);
             takeoff.setVisibility(View.VISIBLE);
             land.setVisibility(View.INVISIBLE);
         }else if(v.getId() == takeoff.getId()){
             throttle(v);
             takeoff.setVisibility(View.INVISIBLE);
             land.setVisibility(View.VISIBLE);
         }else {
             showSettings(v);
         }
    }

    public void showSettings(View v){
        LayoutInflater inflater = (LayoutInflater)getContext().getSystemService(LAYOUT_INFLATER_SERVICE);
        View layout = inflater.inflate(R.layout.settings_modal, (ViewGroup) v.findViewById(R.id.mapFragment));

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setView(layout);

        final TextView altitude = (TextView) layout.findViewById(R.id.altitude);
        final TextView airspeed = (TextView) layout.findViewById(R.id.airspeed);
        final SeekBar seekAltitude = (SeekBar) layout.findViewById(R.id.seekBar1);
        final SeekBar seekAirspeed = (SeekBar) layout.findViewById(R.id.seekBar2);
        txtLat = (TextView) layout.findViewById(R.id.lat);
        txtLng = (TextView) layout.findViewById(R.id.lng);
        txtAlt = (TextView) layout.findViewById(R.id.alt);
        txtVolts = (TextView) layout.findViewById(R.id.voltage);
        txtCur = (TextView) layout.findViewById(R.id.current);
        txtLevel = (TextView) layout.findViewById(R.id.level);
        mode = (TextView) layout.findViewById(R.id.mode);
        armed = (TextView) layout.findViewById(R.id.armed);
        status = (TextView) layout.findViewById(R.id.status);
        seekAltitude.setProgress(droneAltitude);
        seekAirspeed.setProgress(droneAirspeed);
        altitude.setText("Altitude: "+droneAltitude+" m");
        airspeed.setText("Airspeed: "+droneAirspeed+" m/s");

        seekAltitude.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {

            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser){
                altitude.setText("Altitude: "+progress+" m");
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

            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser){
                airspeed.setText("Airspeed: "+progress+" m/s");
                droneAirspeed = progress;
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
        builder.create();
        builder.show();
    }


    @Override
    public void onDestroyView() {
        super.onDestroyView();
        socket.disconnect();
        socket.close();
    }

    public class MapTask extends AsyncTask<String,Integer,String> {

        String command = "";


        @Override
        protected String doInBackground(String... params) {

            command = params[0];
            String json = "";
            URL url = null;
            JSONObject jsonObject = new JSONObject();

            if (params[0] != "getDroneParams"){
                try {

                    if (command.equals("sendRoute")) {
                        JSONArray jsonArrayLatLong = new JSONArray();
                        for (int i = 1; i < point.size()-1; i++) {
                            JSONObject jsonLatLong = new JSONObject();
                            jsonLatLong.accumulate("latitude", point.get(i).latitude);
                            jsonLatLong.accumulate("longitude", point.get(i).longitude);
                            jsonArrayLatLong.put(jsonLatLong);
                        }

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
                    while((line = bufferedReader.readLine()) != null){
                        result += line;
                    }
                    bufferedReader.close();
                    inputStream.close();
                    httpURLConnection.disconnect();

                    //Log.d("Drone",result);
                } catch (JSONException e) {
                    e.printStackTrace();

                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else {

                try {

                    //publishProgress();

                    url = new URL(server+"/params");
                    HttpURLConnection httpURLConnection = (HttpURLConnection) url.openConnection();
                    httpURLConnection.setRequestMethod("GET");
                    httpURLConnection.setRequestProperty("User-Agent", "Mozilla/5.0");
                    int responseCode = httpURLConnection.getResponseCode();
                    System.out.println("GET Response Code :: " + responseCode);

                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        // success
                        BufferedReader in = new BufferedReader(new InputStreamReader(
                                httpURLConnection.getInputStream()));
                        String inputLine;
                        StringBuffer response = new StringBuffer();

                        while ((inputLine = in.readLine()) != null) {
                            response.append(inputLine);
                        }
                        in.close();

                        // print result
                        //Log.d("Drone", response.toString());
                    } else {
                        System.out.println("GET request not worked");
                    }

                } catch (IOException e){
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
        protected void onPostExecute(String s) {
            super.onPostExecute(s);

            if(command.equals("getDroneParams")) {
                initialDialog.dismiss();
                socket.connect();
            }

            if(s.equals("Error!")){
                initialDialog.setMessage("Cannot initialise the vehicle");
                initialDialog.setCanceledOnTouchOutside(true);
            }
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            super.onProgressUpdate(values);
            initialDialog.show();
        }

    }

}
