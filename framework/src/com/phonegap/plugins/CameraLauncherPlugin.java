/*
 * PhoneGap is available under *either* the terms of the modified BSD license *or* the
 * MIT License (2008). See http://opensource.org/licenses/alphabetical for full text.
 * 
 * Copyright (c) 2005-2010, Nitobi Software Inc.
 * Copyright (c) 2010, IBM Corporation
 */
package com.phonegap.plugins;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;
import com.phonegap.api.Plugin;
import com.phonegap.api.PluginResult;
import org.apache.commons.codec.binary.Base64;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;

/**
 * This class launches the camera view, allows the user to take a picture, closes the camera view,
 * and returns the captured image.  When the camera view is closed, the screen displayed before
 * the camera view was shown is redisplayed.
 */
public class CameraLauncherPlugin extends Plugin {
	private static final String TAG = "GAP_" + CameraLauncherPlugin.class.getSimpleName();

	private static final int DATA_URL = 0;			  // Return base64 encoded string
	private static final int FILE_URI = 1;			  // Return file uri (content://media/external/images/media/2 for Android)

	private static final int PHOTOLIBRARY = 0;		  // Choose image from picture library (same as SAVEDPHOTOALBUM for Android)
	private static final int CAMERA = 1;				// Take picture from camera
	private static final int SAVEDPHOTOALBUM = 2;	   // Choose image from picture library (same as PHOTOLIBRARY for Android)

	private static final int PICTURE = 0;			   // allow selection of still pictures only. DEFAULT. Will return format specified via DestinationType
	private static final int VIDEO = 1;				 // allow selection of video only, ONLY RETURNS URL
	private static final int ALLMEDIA = 2;			  // allow selection from all media types

	private static final int JPEG = 0;				  // Take a picture of type JPEG
	private static final int PNG = 1;				   // Take a picture of type PNG
	private static final String GET_PICTURE = "Get Picture";
	private static final String GET_VIDEO = "Get Video";
	private static final String GET_All = "Get All";

	private int mQuality;				   // Compression quality hint (0-100: 0=low quality & high compression, 100=compress of max quality)
	private int targetWidth;				// desired width of the image
	private int targetHeight;			   // desired height of the image
	private Uri imageUri;				   // Uri of captured image
	private int encodingType;			   // Type of encoding to use
	private int mediaType;				  // What type of media to retrieve

	private String callbackId;
	private int numPics;

	/**
	 * Constructor.
	 */
	public CameraLauncherPlugin() {
	}

	/**
	 * Executes the request and returns PluginResult.
	 *
	 * @param action	 The action to execute.
	 * @param args	   JSONArry of arguments for the plugin.
	 * @param callbackId The callback id used when calling back into JavaScript.
	 * @return A PluginResult object with a status and message.
	 */
	@Override
	public PluginResult execute(String action, JSONArray args, String callbackId) {
		PluginResult.Status status = PluginResult.Status.OK;
		String result = "";
		this.callbackId = callbackId;

		try {
			if (action.equals("takePicture")) {
				int srcType = CAMERA;
				int destType = DATA_URL;
				this.targetHeight = 0;
				this.targetWidth = 0;
				this.encodingType = JPEG;
				this.mediaType = PICTURE;
				this.mQuality = 80;

				JSONObject options = args.optJSONObject(0);
				if (options != null) {
					srcType = options.getInt("sourceType");
					destType = options.getInt("destinationType");
					this.targetHeight = options.getInt("targetHeight");
					this.targetWidth = options.getInt("targetWidth");
					this.encodingType = options.getInt("encodingType");
					this.mediaType = options.getInt("mediaType");
					this.mQuality = options.getInt("quality");
				}

				if (srcType == CAMERA) {
					this.takePicture(destType, encodingType);
				} else if ((srcType == PHOTOLIBRARY) || (srcType == SAVEDPHOTOALBUM)) {
					this.getImage(srcType, destType);
				}
				PluginResult r = new PluginResult(PluginResult.Status.NO_RESULT);
				r.setKeepCallback(true);
				return r;
			}
			return new PluginResult(status, result);
		} catch (JSONException e) {
			Log.e(TAG, "JSON error", e);
			e.printStackTrace();
			return new PluginResult(PluginResult.Status.JSON_EXCEPTION);
		}
	}

	//--------------------------------------------------------------------------
	// LOCAL METHODS
	//--------------------------------------------------------------------------

	/**
	 * Take a picture with the camera.
	 * When an image is captured or the camera view is canceled, the result is returned
	 * in PhonegapActivity.onActivityResult, which forwards the result to this.onActivityResult.
	 * <p/>
	 * The image can either be returned as a base64 string or a URI that points to the file.
	 * To display base64 string in an img tag, set the source to:
	 * img.src="data:image/jpeg;base64,"+result;
	 * or to display URI in an img tag
	 * img.src=result;
	 *
	 * @param quality	Compression quality hint (0-100: 0=low quality & high compression, 100=compress of max quality)
	 * @param returnType Set the type of image to return.
	 */
	private void takePicture(int returnType, int encodingType) {
		// Save the number of images currently on disk for later
		this.numPics = queryImgDB().getCount();

		// Display camera
		Intent intent = new Intent("android.media.action.IMAGE_CAPTURE");

		// Specify file so that large image is captured and returned
		// TODO: What if there isn't any external storage?
		File photo = createCaptureFile(encodingType);
		intent.putExtra(android.provider.MediaStore.EXTRA_OUTPUT, Uri.fromFile(photo));
		this.imageUri = Uri.fromFile(photo);

		this.ctx.startActivityForResult((Plugin) this, intent, (CAMERA + 1) * 16 + returnType + 1);
	}

	/**
	 * Create a file in the applications temporary directory based upon the supplied encoding.
	 *
	 * @param encodingType of the image to be taken
	 * @return a File object pointing to the temporary picture
	 */
	private File createCaptureFile(int encodingType) {
		File photo = null;
		if (encodingType == JPEG) {
			photo = new File(DirectoryManager.getTempDirectoryPath(this.context), "Pic.jpg");
		} else {
			photo = new File(DirectoryManager.getTempDirectoryPath(this.context), "Pic.png");
		}
		return photo;
	}

	/**
	 * Get image from photo library.
	 *
	 * @param quality	Compression quality hint (0-100: 0=low quality & high compression, 100=compress of max quality)
	 * @param srcType	The album to get image from.
	 * @param returnType Set the type of image to return.
	 */
	// TODO: Images selected from SDCARD don't display correctly, but from CAMERA ALBUM do!
	private void getImage(int srcType, int returnType) {
		Intent intent = new Intent();
		String title = GET_PICTURE;
		if (this.mediaType == PICTURE) {
			intent.setType("image/*");
		} else if (this.mediaType == VIDEO) {
			intent.setType("video/*");
			title = GET_VIDEO;
		} else if (this.mediaType == ALLMEDIA) {
			// I wanted to make the type 'image/*, video/*' but this does not work on all versions
			// of android so I had to go with the wildcard search.
			intent.setType("*/*");
			title = GET_All;
		}

		intent.setAction(Intent.ACTION_GET_CONTENT);
		intent.addCategory(Intent.CATEGORY_OPENABLE);
		this.ctx.startActivityForResult((Plugin) this, Intent.createChooser(intent,
				title), (srcType + 1) * 16 + returnType + 1);
	}

	/**
	 * Scales the bitmap according to the requested size.
	 *
	 * @param bitmap The bitmap to scale.
	 * @return Bitmap	   A new Bitmap object of the same bitmap after scaling.
	 */
	private Bitmap scaleBitmap(Bitmap bitmap) {
		int newWidth = this.targetWidth;
		int newHeight = this.targetHeight;
		int origWidth = bitmap.getWidth();
		int origHeight = bitmap.getHeight();

		// If no new width or height were specified return the original bitmap
		if (newWidth <= 0 && newHeight <= 0) {
			return bitmap;
		}
		// Only the width was specified
		else if (newWidth > 0 && newHeight <= 0) {
			newHeight = (newWidth * origHeight) / origWidth;
		}
		// only the height was specified
		else if (newWidth <= 0 && newHeight > 0) {
			newWidth = (newHeight * origWidth) / origHeight;
		}
		// If the user specified both a positive width and height
		// (potentially different aspect ratio) then the width or height is
		// scaled so that the image fits while maintaining aspect ratio.
		// Alternatively, the specified width and height could have been
		// kept and Bitmap.SCALE_TO_FIT specified when scaling, but this
		// would result in whitespace in the new image.
		else {
			double newRatio = newWidth / (double) newHeight;
			double origRatio = origWidth / (double) origHeight;

			if (origRatio > newRatio) {
				newHeight = (newWidth * origHeight) / origWidth;
			} else if (origRatio < newRatio) {
				newWidth = (newHeight * origWidth) / origHeight;
			}
		}

		return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true);
	}

	/**
	 * Called when the camera view exits.
	 *
	 * @param requestCode The request code originally supplied to startActivityForResult(),
	 *                    allowing you to identify who this result came from.
	 * @param resultCode  The integer result code returned by the child activity through its setResult().
	 * @param intent	  An Intent, which can return result data to the caller (various data can be attached to Intent "extras").
	 */
	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent intent) {

		// Get src and dest types from request code
		int srcType = (requestCode / 16) - 1;
		int destType = (requestCode % 16) - 1;

		// If CAMERA
		if (srcType == CAMERA) {
			// If image available
			if (resultCode == Activity.RESULT_OK) {
				try {
					// Create an ExifHelper to save the exif data that is lost during compression
					ExifHelper exif = new ExifHelper();
					if (this.encodingType == JPEG) {
						exif.createInFile(DirectoryManager.getTempDirectoryPath(this.context) + "/Pic.jpg");
						exif.readExifData();
					}

					// Read in bitmap of captured image
					Bitmap bitmap;
					try {
						bitmap = android.provider.MediaStore.Images.Media.getBitmap(this.context.getContentResolver(), imageUri);
					} catch (FileNotFoundException e) {
						Uri uri = intent.getData();
						ContentResolver resolver = this.context.getContentResolver();
						bitmap = android.graphics.BitmapFactory.decodeStream(resolver.openInputStream(uri));
					}

					bitmap = scaleBitmap(bitmap);

					// If sending base64 image back
					if (destType == DATA_URL) {
						this.processPicture(bitmap);
						checkForDuplicateImage(DATA_URL);
					}

					// If sending filename back
					else if (destType == FILE_URI) {
						// Create entry in media store for image
						// (Don't use insertImage() because it uses default compression setting of 50 - no way to change it)
						ContentValues values = new ContentValues();
						values.put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
						Uri uri = null;
						try {
							uri = this.context.getContentResolver().insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
						} catch (UnsupportedOperationException e) {
							Log.d(TAG, "Can't write to external media storage.");
							try {
								uri = this.context.getContentResolver().insert(android.provider.MediaStore.Images.Media.INTERNAL_CONTENT_URI, values);
							} catch (UnsupportedOperationException ex) {
								Log.d(TAG, "Can't write to internal media storage.");
								this.failPicture("Error capturing image - no media storage found.");
								return;
							}
						}

						// Add compressed version of captured image to returned media store Uri
						OutputStream os = this.context.getContentResolver().openOutputStream(uri);
						bitmap.compress(Bitmap.CompressFormat.JPEG, this.mQuality, os);
						os.close();

						// Restore exif data to file
						if (this.encodingType == JPEG) {
							exif.createOutFile(FileUtilsPlugin.getRealPathFromURI(context, uri));
							exif.writeExifData();
						}

						// Send Uri back to JavaScript for viewing image
						this.success(new PluginResult(PluginResult.Status.OK, uri.toString()), this.callbackId);
					}
					bitmap.recycle();
					bitmap = null;
					System.gc();

					checkForDuplicateImage(FILE_URI);
				} catch (IOException e) {
					e.printStackTrace();
					this.failPicture("Error capturing image.");
				}
			}

			// If cancelled
			else if (resultCode == Activity.RESULT_CANCELED) {
				this.failPicture("Camera canceled.");
			}

			// If something else
			else {
				this.failPicture("Did not complete!");
			}
		}

		// If retrieving photo from library
		else if ((srcType == PHOTOLIBRARY) || (srcType == SAVEDPHOTOALBUM)) {
			if (resultCode == Activity.RESULT_OK) {
				Uri uri = intent.getData();
				ContentResolver resolver = this.context.getContentResolver();

				// If you ask for video or all media type you will automatically get back a file URI
				// and there will be no attempt to resize any returned data
				if (this.mediaType != PICTURE) {
					this.success(new PluginResult(PluginResult.Status.OK, uri.toString()), this.callbackId);
				} else {
					// If sending base64 image back
					if (destType == DATA_URL) {
						try {
							Bitmap bitmap = android.graphics.BitmapFactory.decodeStream(resolver.openInputStream(uri));
							bitmap = scaleBitmap(bitmap);
							this.processPicture(bitmap);
							bitmap.recycle();
							bitmap = null;
							System.gc();
						} catch (FileNotFoundException e) {
							e.printStackTrace();
							this.failPicture("Error retrieving image.");
						}
					}

					// If sending filename back
					else if (destType == FILE_URI) {
						// Do we need to scale the returned file
						if (this.targetHeight > 0 && this.targetWidth > 0) {
							try {
								Bitmap bitmap = android.graphics.BitmapFactory.decodeStream(resolver.openInputStream(uri));
								bitmap = scaleBitmap(bitmap);

								String fileName = DirectoryManager.getTempDirectoryPath(this.context) + "/resize.jpg";
								OutputStream os = new FileOutputStream(fileName);
								bitmap.compress(Bitmap.CompressFormat.JPEG, this.mQuality, os);
								os.close();

								bitmap.recycle();
								bitmap = null;

								this.success(new PluginResult(PluginResult.Status.OK, ("file://" + fileName)), this.callbackId);
								System.gc();
							} catch (Exception e) {
								e.printStackTrace();
								this.failPicture("Error retrieving image.");
							}
						} else {
							this.success(new PluginResult(PluginResult.Status.OK, uri.toString()), this.callbackId);
						}
					}
				}
			} else if (resultCode == Activity.RESULT_CANCELED) {
				this.failPicture("Selection canceled.");
			} else {
				this.failPicture("Selection did not complete!");
			}
		}
	}

	/**
	 * Creates a cursor that can be used to determine how many images we have.
	 *
	 * @return a cursor
	 */
	private Cursor queryImgDB() {
		return this.context.getContentResolver().query(
				android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
				new String[]{MediaStore.Images.Media._ID},
				null,
				null,
				null);
	}

	/**
	 * Used to find out if we are in a situation where the Camera Intent adds to images
	 * to the content store. If we are using a FILE_URI and the number of images in the DB
	 * increases by 2 we have a duplicate, when using a DATA_URL the number is 1.
	 *
	 * @param type FILE_URI or DATA_URL
	 */
	private void checkForDuplicateImage(int type) {
		int diff = 1;
		Cursor cursor = queryImgDB();
		int currentNumOfImages = cursor.getCount();

		if (type == FILE_URI) {
			diff = 2;
		}

		// delete the duplicate file if the difference is 2 for file URI or 1 for Data URL
		if ((currentNumOfImages - numPics) == diff) {
			cursor.moveToLast();
			int id = Integer.valueOf(cursor.getString(cursor.getColumnIndex(MediaStore.Images.Media._ID))) - 1;
			Uri uri = Uri.parse(MediaStore.Images.Media.EXTERNAL_CONTENT_URI + "/" + id);
			this.context.getContentResolver().delete(uri, null, null);
		}
	}

	/**
	 * Compress bitmap using jpeg, convert to Base64 encoded string, and return to JavaScript.
	 *
	 * @param bitmap
	 */
	private void processPicture(Bitmap bitmap) {
		try {
			ByteArrayOutputStream jpegData = new ByteArrayOutputStream();
			if (bitmap.compress(CompressFormat.JPEG, mQuality, jpegData)) {
				byte[] jpegDataBytes = jpegData.toByteArray();
				byte[] jpegDataBase64 = Base64.encodeBase64(jpegDataBytes);
				//noinspection UnusedAssignment
				jpegDataBytes = null;
				String jsOutput = new String(jpegDataBase64);
				//noinspection UnusedAssignment
				jpegDataBase64 = null;
				this.success(new PluginResult(PluginResult.Status.OK, jsOutput), this.callbackId);
			}
		} catch (Exception e) {
			String err = "Error compressing image.";
			Log.e(TAG, err, e);
			this.failPicture(err);
		}
	}

	/**
	 * Send error message to JavaScript.
	 *
	 * @param err
	 */
	private void failPicture(String err) {
		this.error(new PluginResult(PluginResult.Status.ERROR, err), this.callbackId);
	}
}