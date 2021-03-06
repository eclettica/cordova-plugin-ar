package com.gj.arcoredraw;
/*
 * Copyright 2018 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.ar.core.ArCoreApk;
import com.google.ar.core.AugmentedImage;
import com.google.ar.core.AugmentedImageDatabase;
import com.google.ar.core.Config;
import com.google.ar.core.Frame;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.core.Point;
import com.google.ar.core.Session;
import com.google.ar.core.Trackable;
import com.google.ar.core.TrackingState;
import com.google.ar.core.exceptions.CameraNotAvailableException;
import com.google.ar.core.exceptions.UnavailableApkTooOldException;
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException;
import com.google.ar.core.exceptions.UnavailableSdkTooOldException;
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException;
import com.google.ar.sceneform.FrameTime;
import com.google.ar.sceneform.HitTestResult;
import com.google.ar.sceneform.Node;
import com.google.ar.sceneform.ux.ArFragment;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * This application demonstrates using augmented images to place anchor nodes. app to include image
 * tracking functionality.
 *
 * <p>In this example, we assume all images are static or moving slowly with a large occupation of
 * the screen. If the target is actively moving, we recommend to check
 * ArAugmentedImage_getTrackingMethod() and render only when the tracking method equals to
 * AR_AUGMENTED_IMAGE_TRACKING_METHOD_FULL_TRACKING. See details in <a
 * href="https://developers.google.com/ar/develop/c/augmented-images/">Recognize and Augment
 * Images</a>.
 */
public class AugmentedImageActivitySceneform extends AppCompatActivity {

  private ArFragment arFragment;
  private ImageView fitToScanView;

  // Augmented image and its associated center pose anchor, keyed by the augmented image in
  // the database.
  private final Map<AugmentedImage, AugmentedImageNode> augmentedImageMap = new HashMap<>();
  private final Map<AugmentedImage, AugmentedImageNode> augmentedImageClickedMap = new HashMap<>();
  private Session session;
  private boolean installRequested;
  private boolean shouldConfigureSession = false;
  private final boolean useSingleImage = false;
  private String imagesDatabase = null;
  private List<ListImageElement> imagesList= new LinkedList<ListImageElement>();

  private GestureDetector trackableGestureDetector;




  private static final String TAG = "ARPlugin: it.linup " + AugmentedImageActivitySceneform.class.getSimpleName();



  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(getResources().getIdentifier("activity_mainsceneform", "layout", getPackageName()));

    //arFragment = (ArFragment) getSupportFragmentManager().findFragmentById(R.id.ux_fragment);
    arFragment = (ArFragment) getSupportFragmentManager().findFragmentById(getResources().getIdentifier("ux_fragment", "id", getPackageName()));
    
    //fitToScanView = findViewById(R.id.image_view_fit_to_scan);
    fitToScanView = findViewById(getResources().getIdentifier("image_view_fit_to_scan", "id", getPackageName()));


    arFragment.getArSceneView().getScene().addOnUpdateListener(this::onUpdateFrame);
    //arFragment.getArSceneView().getScene().setOnTouchListener(this::onTouchListner);
    arFragment.getArSceneView().getScene().addOnPeekTouchListener(this::handleOnTouch);

    this.trackableGestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
      public boolean onSingleTapUp(MotionEvent e) {
        onSingleTap(e);
        return true;
      }

      public boolean onDown(MotionEvent e) {
        return true;
      }
    });

    installRequested = false;
    Bundle extras = getIntent().getExtras();
    imagesDatabase = extras.getString("imagesDatabase", null);
    String listImageString = extras.getString("listImages", null);
    Log.d(TAG, "recivedlist: " + listImageString);
    if(listImageString != null) {
      try {
        JSONArray jali = new JSONArray(listImageString);
        if(jali != null) {
          for (int i = 0; i < jali.length(); i++) {
            JSONObject jo = jali.getJSONObject(i);
            if(jo == null)
              continue;
            ListImageElement ile = new ListImageElement();
            ile.idx = jo.getInt("idx");
            ile.imageName = jo.getString("imageName");
            ile.textLabel = jo.getString("textLabel");
            if(jo.has("closeOnClick"))
              ile.closeOnClick = jo.getBoolean("closeOnClick");
            else
              ile.closeOnClick = true;
            imagesList.add(ile);
          }
        }
        Log.d(TAG, "numero elementi: " + imagesList.size());
      } catch (JSONException e) {
        e.printStackTrace();
        Log.e(TAG, "error", e);
      }
    }
  }


  public static class ListImageElement {
    public Integer idx;
    public String imageName;
    public String textLabel;
    public Boolean closeOnClick;
  }

  private void handleOnTouch(HitTestResult hitTestResult, MotionEvent motionEvent) {
    // First call ArFragment's listener to handle TransformableNodes.
    arFragment.onPeekTouch(hitTestResult, motionEvent);

    // Check for touching a Sceneform node
    if (hitTestResult.getNode() == null) {
      return;
    }

    // Otherwise call gesture detector.
    trackableGestureDetector.onTouchEvent(motionEvent);
  }

  private void onSingleTap(MotionEvent motionEvent) {
    Frame frame = arFragment.getArSceneView().getArFrame();
    if (frame != null && motionEvent != null && frame.getCamera().getTrackingState() == TrackingState.TRACKING) {
      for (HitResult hit : frame.hitTest(motionEvent)) {
        Trackable trackable = hit.getTrackable();
        if (trackable instanceof Plane && ((Plane)trackable).isPoseInPolygon(hit.getHitPose())) {
          Plane plane = (Plane)trackable;

          // Handle plane hits.
          break;
        } else if (trackable instanceof Point) {
          // Handle point hits
          Point point = (Point) trackable;

        } else if (trackable instanceof AugmentedImage) {
          // Handle image hits.
          AugmentedImage image = (AugmentedImage) trackable;

          String text = image.getName() + " " + image.getIndex() + " clicked!!!!";
          Log.i(TAG, text);
          SnackbarHelper.getInstance().showMessage(this, text);
          handleClickedAugmentedImage(image);
        }
      }
    }
  }

  private void handleClickedAugmentedImage(AugmentedImage image) {
    if(augmentedImageClickedMap.containsKey(image)) {
      //Era già stato cliccato. Rimuovo il nodo.
      AugmentedImageNode a = augmentedImageClickedMap.get(image);
      a.getAnchor().detach();
      arFragment.getArSceneView().getScene().removeChild(a);
      augmentedImageClickedMap.remove(image);
    } else {
      AugmentedImageNode node = new AugmentedImageNode(this);

      int layoutRef = getResources().getIdentifier("textview_clicked", "layout", getPackageName());
      //TextView textview = (TextView)findViewById(getResources().getIdentifier("textview_clicked", "id", getPackageName()));
      //textview.setText("Prova");
      //View infoCardLayout = findViewById(getResources().getIdentifier("textview_clicked", "layout", getPackageName()));
      //TextView textView = (TextView) infoCardLayout.findViewById(getResources().getIdentifier("textview_clicked", "id", getPackageName()));
      //textView.setText("Prova");

      int textViewId = getResources().getIdentifier("textview_clicked", "id", getPackageName());
      String text = "Info";
      if(imagesList != null && imagesList.size() > image.getIndex()) {
        text = imagesList.get(image.getIndex()).textLabel;
      }
      node.setClickedImage(image, this, this::handleClickedInfoNode, layoutRef, text, textViewId, imagesList.get(image.getIndex()));
      augmentedImageClickedMap.put(image, node);
      arFragment.getArSceneView().getScene().addChild(node);
      //ARPluginCallback.onClick(""+node.getImage().getIndex());
    }
  }


  private void handleClickedInfoNode(AugmentedImageNode node) {
    Log.d(TAG, "handleClickedInfoNode clicked!");
    if(node.getIsInfoNode()) {
      if(node.listImageElement == null)
        ARPluginCallback.onClick("" + node.getImage().getIndex());
      else
        ARPluginCallback.onClick("" + node.listImageElement.idx);
    }
    if(imagesList != null && imagesList.size() > node.getImage().getIndex()) {
      if(imagesList.get(node.getImage().getIndex()).closeOnClick) {
        finish();
      }
    }
  }

  private boolean onTouchListner(HitTestResult hitTestResult, MotionEvent motionEvent) {
    Log.d(TAG,"handleOnTouch");
    // First call ArFragment's listener to handle TransformableNodes.
    arFragment.onPeekTouch(hitTestResult, motionEvent);

    //We are only interested in the ACTION_UP events - anything else just return
    if (motionEvent.getAction() != MotionEvent.ACTION_UP) {
      return false;
    }

    // Check for touching a Sceneform node
    if (hitTestResult.getNode() != null) {
      Log.d(TAG,"handleOnTouch hitTestResult.getNode() != null");
      Node hitNode = hitTestResult.getNode();

      /*if (hitNode.getRenderable() == andyRenderable) {
        arFragment.getArSceneView().getScene().removeChild(hitNode);
        AnchorNode hitNodeAnchor = (AnchorNode) hitNode;
        if (hitNodeAnchor != null) {
          hitNode.getAnchor().detach();
        }
        hitNode.setParent(null);
        hitNode = null;
      }*/
    }
    return true;
  }

  /*@Override
  protected void onResume() {
    super.onResume();
    if (augmentedImageMap.isEmpty()) {
      fitToScanView.setVisibility(View.VISIBLE);
    }
  }*/

  @Override
  protected void onResume() {
    super.onResume();

    if (session == null) {
      Exception exception = null;
      String message = null;
      try {
        switch (ArCoreApk.getInstance().requestInstall(this, !installRequested)) {
          case INSTALL_REQUESTED:
            installRequested = true;
            return;
          case INSTALLED:
            break;
        }

        // ARCore requires camera permissions to operate. If we did not yet obtain runtime
        // permission on Android M and above, now is a good time to ask the user for it.
        if (!CameraPermissionHelper.hasCameraPermission(this)) {
          CameraPermissionHelper.requestCameraPermission(this);
          return;
        }

        session = new Session(/* context = */ this);
      } catch (UnavailableArcoreNotInstalledException
              | UnavailableUserDeclinedInstallationException e) {
        message = "Please install ARCore";
        exception = e;
      } catch (UnavailableApkTooOldException e) {
        message = "Please update ARCore";
        exception = e;
      } catch (UnavailableSdkTooOldException e) {
        message = "Please update this app";
        exception = e;
      } catch (Exception e) {
        message = "This device does not support AR";
        exception = e;
      }

      if (message != null) {
        SnackbarHelper.getInstance().showError(this, message);
        Log.e(TAG, "Exception creating session", exception);
        return;
      }

      shouldConfigureSession = true;
    }

    if (shouldConfigureSession) {
      configureSession();
      shouldConfigureSession = false;
    }

    // Note that order matters - see the note in onPause(), the reverse applies here.
    try {
      session.resume();
    } catch (CameraNotAvailableException e) {
      SnackbarHelper.getInstance().showError(this, "Camera not available. Try restarting the app.");
      session = null;
      return;
    }
    /*surfaceView.onResume();
    displayRotationHelper.onResume();*/

    if (augmentedImageMap.isEmpty()) {
      fitToScanView.setVisibility(View.VISIBLE);
    }
  }

  private void configureSession() {
    Config config = new Config(session);
    config.setFocusMode(Config.FocusMode.AUTO);
    if (!setupAugmentedImageDatabase(config)) {
      SnackbarHelper.getInstance().showError(this, "Could not setup augmented image database");
    }
    session.configure(config);
  }
  private boolean setupAugmentedImageDatabase(Config config) {
    AugmentedImageDatabase augmentedImageDatabase;

    // There are two ways to configure an AugmentedImageDatabase:
    // 1. Add Bitmap to DB directly
    // 2. Load a pre-built AugmentedImageDatabase
    // Option 2) has
    // * shorter setup time
    // * doesn't require images to be packaged in apk.
    if (useSingleImage) {
      Bitmap augmentedImageBitmap = loadAugmentedImageBitmap();
      if (augmentedImageBitmap == null) {
        return false;
      }

      augmentedImageDatabase = new AugmentedImageDatabase(session);
      augmentedImageDatabase.addImage("image_name", augmentedImageBitmap);
      // If the physical size of the image is known, you can instead use:
      //     augmentedImageDatabase.addImage("image_name", augmentedImageBitmap, widthInMeters);
      // This will improve the initial detection speed. ARCore will still actively estimate the
      // physical size of the image as it is viewed from multiple viewpoints.
    } else {
      // This is an alternative way to initialize an AugmentedImageDatabase instance,
      // load a pre-existing augmented image database.
      Log.d(TAG, "read database");
      if(imagesDatabase == null) {
        try (InputStream is = getAssets().open("sample_database.imgdb")) {
          Log.d(TAG, "deserialize database");
          augmentedImageDatabase = AugmentedImageDatabase.deserialize(session, is);
        } catch (IOException e) {
          Log.e(TAG, "IO exception loading augmented image database.", e);
          return false;
        }
      } else {
        try {
          FileInputStream fis = this.openFileInput(imagesDatabase);
          augmentedImageDatabase = AugmentedImageDatabase.deserialize(session, fis);
        } catch (IOException e) {
          Log.e(TAG, "IO exception loading augmented image database.", e);
          return false;
        }
      }
    }
    Log.d(TAG, "deserialized!");
    config.setAugmentedImageDatabase(augmentedImageDatabase);
    Log.d(TAG, "database set!");
    Log.d(TAG, "number of images in database: " + augmentedImageDatabase.getNumImages());
    return true;
  }

  private Bitmap loadAugmentedImageBitmap() {
    try (InputStream is = getAssets().open("default.jpg")) {
      return BitmapFactory.decodeStream(is);
    } catch (IOException e) {
      Log.e(TAG, "IO exception loading augmented image bitmap.", e);
    }
    return null;
  }


  /**
   * Registered with the Sceneform Scene object, this method is called at the start of each frame.
   *
   * @param frameTime - time since last frame.
   */
  private void onUpdateFrame(FrameTime frameTime) {
    Frame frame = arFragment.getArSceneView().getArFrame();

    // If there is no frame, just return.
    if (frame == null) {
      return;
    }

    Collection<AugmentedImage> updatedAugmentedImages =
        frame.getUpdatedTrackables(AugmentedImage.class);
    for (AugmentedImage augmentedImage : updatedAugmentedImages) {
      switch (augmentedImage.getTrackingState()) {
        case PAUSED:
          // When an image is in PAUSED state, but the camera is not PAUSED, it has been detected,
          // but not yet tracked.
          String text = "Detected Image " + augmentedImage.getIndex();
          //Log.d(TAG, "onUpdateFrame text: " + text);
          SnackbarHelper.getInstance().showMessage(this, text);
          break;

        case TRACKING:
          // Have to switch to UI Thread to update View.
          //Log.d(TAG, "onUpdateFrame fitToScan setVisibility ");
          fitToScanView.setVisibility(View.GONE);
          //Log.d(TAG, "onUpdateFrame fitToScan gone ");
          // Create a new anchor for newly found images.
          if (!augmentedImageMap.containsKey(augmentedImage)) {
            //Log.d(TAG, "onUpdateFrame create node ");
            AugmentedImageNode node = new AugmentedImageNode(this);
            //Log.d(TAG, "onUpdateFrame node created");
            node.setImage(augmentedImage, this);
            //Log.d(TAG, "onUpdateFrame node image set");
            augmentedImageMap.put(augmentedImage, node);
            //Log.d(TAG, "onUpdateFrame node map put");
            arFragment.getArSceneView().getScene().addChild(node);
            //Log.d(TAG, "onUpdateFrame added node ");

          } else {
            //Log.d(TAG, "onUpdateFrame node already created");
          }
          break;

        case STOPPED:
          augmentedImageMap.remove(augmentedImage);
          break;
      }
    }
  }
}