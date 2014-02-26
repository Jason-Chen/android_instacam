/*
   Copyright 2012 Harri Smatt

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
 */

package fi.harism.instacam;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Calendar;
import java.util.Locale;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.SurfaceTexture;
import android.hardware.SensorManager;
import android.media.ExifInterface;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.MediaStore.Images;
import android.provider.MediaStore.Images.ImageColumns;
import android.provider.MediaStore.MediaColumns;
import android.view.OrientationEventListener;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

/**
 * The one and only Activity for this camera application.
 */
public class InstaCamActivity extends Activity {

	// Custom camera holder class.
	private final InstaCamCamera mCamera = new InstaCamCamera();
	// RenderScript holder class.
	private InstaCamRS mInstaCamRS;
	// Common observer for all Buttons.
	private final ButtonObserver mObserverButton = new ButtonObserver();
	// Camera observer for handling picture taking.
	private final CameraObserver mObserverCamera = new CameraObserver();
	// Device orientation observer.
	private OrientationObserver mObserverOrientation;
	// Observer for handling SurfaceTexture creation.
	private final RendererObserver mObserverRenderer = new RendererObserver();
	// Common observer for all SeekBars.
	private final SeekBarObserver mObserverSeekBar = new SeekBarObserver();
	// Common observer for all Spinners.
	private final SpinnerObserver mObserverSpinner = new SpinnerObserver();
	// Application shared preferences instance.
	private SharedPreferences mPreferences;
	// Preview texture renderer class.
	private InstaCamRenderer mRenderer;
	// Shared data instance.
	private final InstaCamData mSharedData = new InstaCamData();

	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		// We prevent screen orientation changes.
		super.onConfigurationChanged(newConfig);
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// Force full screen view.
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
		getWindow().clearFlags(
				WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);

		// Instantiate RenderScript.
		mInstaCamRS = new InstaCamRS(this);
		// Instantieate device orientation observer.
		mObserverOrientation = new OrientationObserver(this);

		// Instantiate camera handler.
		mCamera.setCameraFront(false);
		mCamera.setSharedData(mSharedData);

		// Set content view.
		setContentView(R.layout.instacam);

		// Set filter spinner adapter.
		Spinner filterSpinner = (Spinner) findViewById(R.id.spinner_filter);
		ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
				this, R.array.filters, R.layout.spinner_text);
		// adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		adapter.setDropDownViewResource(R.layout.spinner_dropdown);
		filterSpinner.setAdapter(adapter);

		// Find renderer view and instantiate it.
		mRenderer = (InstaCamRenderer) findViewById(R.id.instacam_renderer);
		mRenderer.setSharedData(mSharedData);
		mRenderer.setObserver(mObserverRenderer);

		// Hide menu view by default.
		View menu = findViewById(R.id.menu);
		menu.setVisibility(View.GONE);

		// Set Button OnClickListeners.
		findViewById(R.id.button_exit).setOnClickListener(mObserverButton);
		findViewById(R.id.button_shoot).setOnClickListener(mObserverButton);
		findViewById(R.id.button_save).setOnClickListener(mObserverButton);
		findViewById(R.id.button_cancel).setOnClickListener(mObserverButton);
		findViewById(R.id.button_menu).setOnClickListener(mObserverButton);
		findViewById(R.id.button_rotate).setOnClickListener(mObserverButton);

		// Get preferences instance.
		mPreferences = getPreferences(MODE_PRIVATE);

		// Set observer for filter Spinner.
		filterSpinner.setOnItemSelectedListener(mObserverSpinner);
		mSharedData.mFilter = mPreferences.getInt(
				getString(R.string.key_filter), 0);
		filterSpinner.setSelection(mSharedData.mFilter);

		// SeekBar ids as triples { SeekBar id, key id, default value }.
		final int SEEKBAR_IDS[][] = {
				{ R.id.seekbar_brightness, R.string.key_brightness, 5 },
				{ R.id.seekbar_contrast, R.string.key_contrast, 5 },
				{ R.id.seekbar_saturation, R.string.key_saturation, 8 },
				{ R.id.seekbar_corner_radius, R.string.key_corner_radius, 3 } };
		// Set SeekBar OnSeekBarChangeListeners and default progress.
		for (int ids[] : SEEKBAR_IDS) {
			SeekBar seekBar = (SeekBar) findViewById(ids[0]);
			seekBar.setOnSeekBarChangeListener(mObserverSeekBar);
			seekBar.setProgress(mPreferences.getInt(getString(ids[1]), ids[2]));
			// SeekBar.setProgress triggers observer only in case its value
			// changes. And we're relying on this trigger to happen.
			if (seekBar.getProgress() == 0) {
				seekBar.setProgress(1);
				seekBar.setProgress(0);
			}
		}
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
	}

	@Override
	public void onPause() {
		super.onPause();
		mCamera.onPause();
		mRenderer.onPause();
		mObserverOrientation.disable();
	}

	@Override
	public void onResume() {
		super.onResume();
		mCamera.onResume();
		mRenderer.onResume();

		if (mObserverOrientation.canDetectOrientation()) {
			mObserverOrientation.enable();
		}
	}

	private final void setCameraFront(final boolean front) {
		View button = findViewById(R.id.button_rotate);

		PropertyValuesHolder holderRotation = PropertyValuesHolder.ofFloat(
				"rotation", button.getRotation(), 360);
		ObjectAnimator anim = ObjectAnimator.ofPropertyValuesHolder(button,
				holderRotation).setDuration(700);
		anim.addListener(new Animator.AnimatorListener() {
			@Override
			public void onAnimationCancel(Animator animation) {
			}

			@Override
			public void onAnimationEnd(Animator animation) {
				findViewById(R.id.button_rotate).setRotation(0);
				mCamera.setCameraFront(front);
			}

			@Override
			public void onAnimationRepeat(Animator animation) {
			}

			@Override
			public void onAnimationStart(Animator animation) {
			}
		});
		anim.start();
	}

	private final void setMenuVisible(boolean visible) {
		View menu = findViewById(R.id.menu);
		View button = findViewById(R.id.button_menu);

		if (visible) {
			// Animate menu visible
			menu.setPivotY(0);
			menu.setVisibility(View.VISIBLE);
			PropertyValuesHolder holderAlpha = PropertyValuesHolder.ofFloat(
					"alpha", menu.getAlpha(), 1);
			PropertyValuesHolder holderScale = PropertyValuesHolder.ofFloat(
					"scaleY", menu.getScaleY(), 1);
			ObjectAnimator anim = ObjectAnimator.ofPropertyValuesHolder(menu,
					holderAlpha, holderScale).setDuration(500);
			anim.start();

			// Animate menu button "upside down"
			holderScale = PropertyValuesHolder.ofFloat("scaleY",
					button.getScaleY(), -1);
			anim = ObjectAnimator.ofPropertyValuesHolder(button, holderScale)
					.setDuration(500);
			anim.start();
		} else {
			// Hide menu
			menu.setPivotY(0);
			PropertyValuesHolder holderAlpha = PropertyValuesHolder.ofFloat(
					"alpha", menu.getAlpha(), 0);
			PropertyValuesHolder holderScale = PropertyValuesHolder.ofFloat(
					"scaleY", menu.getScaleY(), 0);
			ObjectAnimator anim = ObjectAnimator.ofPropertyValuesHolder(menu,
					holderAlpha, holderScale).setDuration(500);
			anim.addListener(new Animator.AnimatorListener() {
				@Override
				public void onAnimationCancel(Animator animation) {
				}

				@Override
				public void onAnimationEnd(Animator animation) {
					findViewById(R.id.menu).setVisibility(View.GONE);
				}

				@Override
				public void onAnimationRepeat(Animator animation) {
				}

				@Override
				public void onAnimationStart(Animator animation) {
				}
			});
			anim.start();

			// Animate menu button back to "normal"
			holderScale = PropertyValuesHolder.ofFloat("scaleY",
					button.getScaleY(), 1);
			anim = ObjectAnimator.ofPropertyValuesHolder(button, holderScale)
					.setDuration(500);
			anim.start();
		}
	}

	private final class ButtonObserver implements View.OnClickListener {
		@Override
		public void onClick(View v) {
			switch (v.getId()) {
			// On exit simply close Activity.
			case R.id.button_exit:
				finish();
				break;
			// On shoot trigger picture taking.
			case R.id.button_shoot:
				// We do not want to receive orientation changes until picture
				// is either saved or cancelled.
				mObserverOrientation.disable();
				mCamera.takePicture(mObserverCamera);
				break;
			// Pressing menu button switches menu visibility.
			case R.id.button_menu:
				View view = findViewById(R.id.menu);
				setMenuVisible(view.getVisibility() != View.VISIBLE);
				break;
			// Save button starts separate thread for saving picture.
			case R.id.button_save:
				mSharedData.mImageProgress = ProgressDialog
						.show(InstaCamActivity.this, null,
								getString(R.string.saving));
				Thread thread = new Thread(new SaveRunnable());
				thread.setPriority(Thread.MAX_PRIORITY);
				thread.start();
				break;
			// Cancel button simply discards current picture data.
			case R.id.button_cancel:
				mSharedData.mImageData = null;
				findViewById(R.id.buttons_shoot).setVisibility(View.VISIBLE);
				findViewById(R.id.buttons_cancel_save).setVisibility(View.GONE);
				mCamera.startPreview();
				// Re-enable orientation observer.
				mObserverOrientation.enable();
				break;
			case R.id.button_rotate:
				setCameraFront(!mCamera.isCameraFront());
				break;
			}
		}

	}

	/**
	 * Class for implementing Camera related callbacks.
	 */
	private final class CameraObserver implements InstaCamCamera.Observer {
		@Override
		public void onAutoFocus(boolean success) {
			// If auto focus failed show brief notification about it.
			if (!success) {
				Toast.makeText(InstaCamActivity.this, R.string.focus_failed,
						Toast.LENGTH_SHORT).show();
			}
		}

		@Override
		public void onPictureTaken(byte[] data) {
			// Once picture is taken just store its data.
			mSharedData.mImageData = data;
			// And time it was taken.
			Calendar calendar = Calendar.getInstance();
			mSharedData.mImageTime = calendar.getTimeInMillis();
		}

		@Override
		public void onShutter() {
			// At the point picture is actually taken switch footer buttons.
			findViewById(R.id.buttons_cancel_save).setVisibility(View.VISIBLE);
			findViewById(R.id.buttons_shoot).setVisibility(View.GONE);
		}

	}

	/**
	 * Class for observing device orientation.
	 */
	private class OrientationObserver extends OrientationEventListener {

		public OrientationObserver(Context context) {
			super(context, SensorManager.SENSOR_DELAY_NORMAL);
			disable();
		}

		@Override
		public void onOrientationChanged(int orientation) {
			orientation = (((orientation + 45) / 90) * 90) % 360;
			if (orientation != mSharedData.mOrientationDevice) {

				// Prevent 270 degree turns.
				int original = mSharedData.mOrientationDevice;
				if (Math.abs(orientation - original) > 180) {
					if (orientation > original) {
						original += 360;
					} else {
						original -= 360;
					}
				}

				// Trigger rotation animation.
				View shoot = findViewById(R.id.button_shoot);
				PropertyValuesHolder holderRotation = PropertyValuesHolder
						.ofFloat("rotation", -original, -orientation);
				ObjectAnimator anim = ObjectAnimator.ofPropertyValuesHolder(
						shoot, holderRotation).setDuration(500);
				anim.start();

				// Store and calculate new orientation values.
				mSharedData.mOrientationDevice = orientation;
				mCamera.updateRotation();
			}
		}

	}

	/**
	 * Class for implementing InstaCamRenderer related callbacks.
	 */
	private class RendererObserver implements InstaCamRenderer.Observer {
		@Override
		public void onSurfaceTextureCreated(SurfaceTexture surfaceTexture) {
			// Once we have SurfaceTexture try setting it to Camera.
			try {
				mCamera.stopPreview();
				mCamera.setPreviewTexture(surfaceTexture);

				// Start preview only if shoot -button is visible. Otherwise we
				// do have image captured for later use.
				if (findViewById(R.id.buttons_shoot).getVisibility() == View.VISIBLE)
					mCamera.startPreview();

			} catch (final Exception ex) {
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						Toast.makeText(InstaCamActivity.this, ex.getMessage(),
								Toast.LENGTH_LONG).show();
					}
				});
			}
		}
	}

	/**
	 * Runnable for handling asynchronous picture saving.
	 */
	private final class SaveRunnable implements Runnable {

		@Override
		public void run() {
			String error = null;
			try {
				// First instantiate calendar instance.
				Calendar calendar = Calendar.getInstance();
				calendar.setTimeInMillis(mSharedData.mImageTime);

				// Generate picture name.
				String pictureName = String.format(Locale.getDefault(),
						"InstaCam_%d%02d%02d_%02d%02d%02d",
						calendar.get(Calendar.YEAR),
						calendar.get(Calendar.MONTH) + (1 - Calendar.JANUARY),
						calendar.get(Calendar.DATE),
						calendar.get(Calendar.HOUR_OF_DAY),
						calendar.get(Calendar.MINUTE),
						calendar.get(Calendar.SECOND));

				// Get "Pictures" -directory path.
				File filePath = Environment
						.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
				// Add "/InstaCam/" -directory to it.
				filePath = new File(filePath, "/InstaCam/");
				// Make all dirs till ".../Pictures/InstaCam/".
				filePath.mkdirs();
				// Generate final file path ".../InstaCam/picname.jpeg".
				filePath = new File(filePath, pictureName + ".jpeg");
				// Create picture file.
				filePath.createNewFile();

				// We'd prefer to have ARGB_8888 Bitmap.
				BitmapFactory.Options options = new BitmapFactory.Options();
				options.inPreferredConfig = Bitmap.Config.ARGB_8888;
				// Decode picture taken by camera.
				Bitmap bitmap = BitmapFactory.decodeByteArray(
						mSharedData.mImageData, 0,
						mSharedData.mImageData.length, options);
				// Apply RenderScript filter to Bitmap.
				mInstaCamRS.applyFilter(bitmap, mSharedData);
				// Save picture to file system.
				FileOutputStream fos = new FileOutputStream(filePath);
				bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos);
				fos.flush();
				fos.close();
				bitmap.recycle();

				ExifInterface exif = new ExifInterface(
						filePath.getAbsolutePath());
				switch (mCamera.getOrientation()) {
				case 90:
					exif.setAttribute(ExifInterface.TAG_ORIENTATION, Integer
							.toString(ExifInterface.ORIENTATION_ROTATE_90));
					break;
				case 180:
					exif.setAttribute(ExifInterface.TAG_ORIENTATION, Integer
							.toString(ExifInterface.ORIENTATION_ROTATE_180));
					break;
				case 270:
					exif.setAttribute(ExifInterface.TAG_ORIENTATION, Integer
							.toString(ExifInterface.ORIENTATION_ROTATE_270));
					break;
				default:
					exif.setAttribute(ExifInterface.TAG_ORIENTATION,
							Integer.toString(ExifInterface.ORIENTATION_NORMAL));
					break;
				}
				exif.saveAttributes();

				// Add picture to content resolver.
				ContentValues v = new ContentValues();
				v.put(MediaColumns.TITLE, pictureName);
				v.put(MediaColumns.DISPLAY_NAME, pictureName);
				v.put(ImageColumns.DESCRIPTION, "Taken with InstaCam.");
				v.put(MediaColumns.DATE_ADDED, calendar.getTimeInMillis());
				v.put(ImageColumns.DATE_TAKEN, calendar.getTimeInMillis());
				v.put(MediaColumns.DATE_MODIFIED, calendar.getTimeInMillis());
				v.put(MediaColumns.MIME_TYPE, "image/jpeg");
				v.put(ImageColumns.ORIENTATION, mCamera.getOrientation());
				v.put(MediaColumns.DATA, filePath.getAbsolutePath());

				File parent = filePath.getParentFile();
				String path = parent.toString()
						.toLowerCase(Locale.getDefault());
				String name = parent.getName().toLowerCase(Locale.getDefault());
				v.put(Images.ImageColumns.BUCKET_ID, path.hashCode());
				v.put(Images.ImageColumns.BUCKET_DISPLAY_NAME, name);
				v.put(MediaColumns.SIZE, filePath.length());

				// if( targ_loc != null ) {
				// v.put(Images.Media.LATITUDE, loc.getLatitude());
				// v.put(Images.Media.LONGITUDE, loc.getLongitude());
				// }

				ContentResolver c = getContentResolver();
				c.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
			} catch (Exception ex) {
				error = ex.getMessage();
			}

			// Finally hide progress dialog, dismiss picture data and show error
			// message if an error occurred during saving.
			final String errorMsg = error;
			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					mSharedData.mImageProgress.dismiss();
					mSharedData.mImageProgress = null;
					mSharedData.mImageData = null;
					findViewById(R.id.buttons_shoot)
							.setVisibility(View.VISIBLE);
					findViewById(R.id.buttons_cancel_save).setVisibility(
							View.GONE);
					mCamera.startPreview();
					mObserverOrientation.enable();
					if (errorMsg != null) {
						Toast.makeText(InstaCamActivity.this, errorMsg,
								Toast.LENGTH_LONG).show();
					}
				}
			});
		}
	}

	/**
	 * Class for implementing SeekBar related callbacks.
	 */
	private final class SeekBarObserver implements
			SeekBar.OnSeekBarChangeListener {

		@Override
		public void onProgressChanged(SeekBar seekBar, int progress,
				boolean fromUser) {

			switch (seekBar.getId()) {
			// On brightness recalculate shared value and update preferences.
			case R.id.seekbar_brightness: {
				mPreferences.edit()
						.putInt(getString(R.string.key_brightness), progress)
						.commit();
				mSharedData.mBrightness = (progress - 5) / 10f;

				TextView textView = (TextView) findViewById(R.id.text_brightness);
				textView.setText(getString(R.string.seekbar_brightness,
						progress - 5));
				break;
			}
			// On contrast recalculate shared value and update preferences.
			case R.id.seekbar_contrast: {
				mPreferences.edit()
						.putInt(getString(R.string.key_contrast), progress)
						.commit();
				mSharedData.mContrast = (progress - 5) / 10f;
				TextView textView = (TextView) findViewById(R.id.text_contrast);
				textView.setText(getString(R.string.seekbar_contrast,
						progress - 5));
				break;
			}
			// On saturation recalculate shared value and update preferences.
			case R.id.seekbar_saturation: {
				mPreferences.edit()
						.putInt(getString(R.string.key_saturation), progress)
						.commit();
				mSharedData.mSaturation = (progress - 5) / 10f;
				TextView textView = (TextView) findViewById(R.id.text_saturation);
				textView.setText(getString(R.string.seekbar_saturation,
						progress - 5));
				break;
			}
			// On radius recalculate shared value and update preferences.
			case R.id.seekbar_corner_radius: {
				mPreferences
						.edit()
						.putInt(getString(R.string.key_corner_radius), progress)
						.commit();
				mSharedData.mCornerRadius = progress / 10f;
				TextView textView = (TextView) findViewById(R.id.text_corner_radius);
				textView.setText(getString(R.string.seekbar_corner_radius,
						-progress));
				break;
			}
			}
			mRenderer.requestRender();
		}

		@Override
		public void onStartTrackingTouch(SeekBar seekBar) {
		}

		@Override
		public void onStopTrackingTouch(SeekBar seekBar) {
		}
	}

	/**
	 * Class for implementing Spinner related callbacks.
	 */
	private class SpinnerObserver implements AdapterView.OnItemSelectedListener {
		@Override
		public void onItemSelected(AdapterView<?> parent, View view,
				int position, long id) {
			mPreferences.edit()
					.putInt(getString(R.string.key_filter), position).commit();
			mSharedData.mFilter = position;
			mRenderer.requestRender();
		}

		@Override
		public void onNothingSelected(AdapterView<?> parent) {
		}
	}

}