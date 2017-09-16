package raniel.earthquakesearchdrone;

import android.hardware.Camera;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

/**
 * Created by Raniel on 5/19/2017.
 */

public class NormalCamera extends Fragment {

    FrameLayout preview;

    private Camera mCamera;
    private CameraPreview mPreview;

    private static final String TAG = "Drone";

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_normal_camera, container, false);

        mCamera = getCameraInstance();

        // Create our Preview view and set it as the content of our activity.

        mPreview = new CameraPreview(getContext(), mCamera);

        preview = (FrameLayout) view.findViewById(R.id.normal_camera_preview);

        preview.addView(mPreview);

        return view;
    }

    /** A safe way to get an instance of the Camera object. */
    private Camera getCameraInstance(){
        Camera c = null;
        try {
            c = Camera.open(); // attempt to get a Camera instance
        }
        catch (Exception e){
            // Camera is not available (in use or does not exist)
            Log.d(TAG, "NormalCamera - Camera is not available (in use or does not exist)");
            e.printStackTrace();
        }

        return c; // returns null if camera is unavailable
    }


}
