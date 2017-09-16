package raniel.earthquakesearchdrone;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.app.ActionBar;
import android.view.MenuItem;
import android.widget.TextView;


import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.places.Places;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

import org.w3c.dom.Text;

/**
 * An activity representing a single View detail screen. This
 * activity is only used narrow width devices. On tablet-size devices,
 * item details are presented side-by-side with a list of items
 * in a {@link ViewListActivity}.
 */
public class ViewDetailActivity extends AppCompatActivity {

    private GoogleMap mMap;
    private boolean mLocationPermissionGranted;
    private static final int PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 1;
    private Location mLastKnownLocation;
    private static final String TAG = ViewDetailActivity.class.getSimpleName();
    private static final int DEFAULT_ZOOM = 19;
    // CEA - 14.599458, 121.005365
    private final LatLng mDefaultLocation = new LatLng(14.598918, 121.005397);
    private CameraPosition mCameraPosition;
    private GoogleApiClient mGoogleApiClient;

    private String view = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_detail);
        /*
        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            buildAlertMessageNoGps();
        }

        // Do other setup activities here too, as described elsewhere in this tutorial.

        // Build the Play services client for use by the Fused Location Provider and the Places API.
        // Use the addApi() method to request the Google Places API and the Fused Location Provider.

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .enableAutoManage(this /* FragmentActivity /,
                        this /* OnConnectionFailedListener /)
                .addConnectionCallbacks(this)
                .addApi(LocationServices.API)
                .addApi(Places.GEO_DATA_API)
                .addApi(Places.PLACE_DETECTION_API)
                .build();
        mGoogleApiClient.connect();
        */
        if (savedInstanceState == null) {

            Bundle arguments = new Bundle();
            /*
            arguments.putString(ViewDetailFragment.ARG_ITEM_ID,
                    getIntent().getStringExtra(ViewDetailFragment.ARG_ITEM_ID));
            */
            Bundle extras = getIntent().getExtras();

            if (extras != null) {
                view = extras.getString("View");
            }else {
                view = savedInstanceState.getString("View");
            }
            Log.d(TAG, "ViewDetailActivity - "+view);


            if(view.equals("Google Map")){
                MapFragment mapFragment = new MapFragment();
                mapFragment.setArguments(arguments);
                getSupportFragmentManager().beginTransaction()
                        .add(R.id.fragment_container, mapFragment)
                        .commit();
            }else if(view.equals("Thermal Camera")){
                ThermalCamera thermalCameraFragment = new ThermalCamera();
                thermalCameraFragment.setArguments(arguments);
                getSupportFragmentManager().beginTransaction()
                        .add(R.id.fragment_container, thermalCameraFragment)
                        .commit();
            }else if(view.equals("Normal Camera")){
                NormalCamera normalCameraFragment = new NormalCamera();
                normalCameraFragment.setArguments(arguments);
                getSupportFragmentManager().beginTransaction()
                        .add(R.id.fragment_container, normalCameraFragment)
                        .commit();
            }

        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            navigateUpTo(new Intent(this, ViewListActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

}
