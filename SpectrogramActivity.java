/**
 * Spectrogram Android application
 * Copyright (c) 2013 Guillaume Adam  http://www.galmiza.net/

 * This software is provided 'as-is', without any express or implied warranty.
 * In no event will the authors be held liable for any damages arising from the use of this software.
 * Permission is granted to anyone to use this software for any purpose,
 * including commercial applications, and to alter it and redistribute it freely,
 * subject to the following restrictions:

 * 1. The origin of this software must not be misrepresented; you must not claim that you wrote the original software. If you use this software in a product, an acknowledgment in the product documentation would be appreciated but is not required.
 * 2. Altered source versions must be plainly marked as such, and must not be misrepresented as being the original software.
 * 3. This notice may not be removed or altered from any source distribution.
 */

package net.galmiza.android.spectrogram;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.galmiza.android.spectrogram.Misc;
import net.galmiza.android.engine.sound.SoundEngine;
import net.galmiza.android.spectrogram.ContinuousRecord.OnBufferReadyListener;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.support.v4.content.ContextCompat;
import android.support.v4.app.ActivityCompat;
import android.content.pm.PackageManager;
import android.widget.Toast;

/**
 * Entry point of the application
 * Handles:
 *  recording service
 *  fft processing by calling native functions
 *  view updates
 *  activity events:
 *   onCreate, onDestroy,
 *   onCreateOptionsMenu, onOptionsItemSelected, (top menu banner)
 *   onActivityResult (response from external activities)
 */
public class SpectrogramActivity extends AppCompatActivity {
	
	// Constant
	static final float PI = (float) Math.PI;
	static final int INTENT_SETTINGS = 0;
    static final int MY_PERMISSIONS_REQUEST_RECORD_AUDIO = 0;
	static final int MY_PERMISSIONS_REQUEST_WRITE = 1;
	
	// Attributes
	private Thread thread;
	private FrequencyView frequencyView;
	private ContinuousRecord recorder;
	private SoundEngine nativeLib;
	private Menu menu;
	private int samplingRate = 44100;
	private int fftResolution;
	public ArrayList<short[]> audioFile;
	public ArrayList<short[]> audioFileTest;
	private Button startButton,stopButton,viewButton,replayButton,resetButton;
	
	// Buffers
	private List<short[]> bufferStack; // Store trunks of buffers
	private short[] fftBuffer; // buffer supporting the fft process
	private float[] re; // buffer holding real part during fft process
	private float[] im; // buffer holding imaginary part during fft process
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		// Share core
		Misc.setAttribute("activity", this);
		
		// Load preferences
		loadPreferences();
		
		// JNI interface
		nativeLib = new SoundEngine();
		nativeLib.initFSin();
		
		// Recorder & player
		recorder = new ContinuousRecord(samplingRate);

		// Request record audio permission
		if (!(ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)) {
			ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.RECORD_AUDIO}, MY_PERMISSIONS_REQUEST_RECORD_AUDIO);
		}
		
		// Create view for frequency display
		setContentView(R.layout.main);
		frequencyView = (FrequencyView) findViewById(R.id.frequency_view);
		if (Misc.getPreference(this, "keep_screen_on", false))
			frequencyView.setKeepScreenOn(true);
		frequencyView.setFFTResolution(fftResolution);
		frequencyView.setSamplingRate(samplingRate);
		
		// Color mode
		boolean nightMode = Misc.getPreference(this, "night_mode", true);
		if (!nightMode)	{
        	frequencyView.setBackgroundColor(Color.WHITE);
        } else {
        	frequencyView.setBackgroundColor(Color.BLACK);
        }

        /*// Prepare screen
        getSupportActionBar().hide();
        if (util.Misc.getPreference(this, "hide_status_bar", false))
        	getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);*/

		//Request write permissions
			//instancie le fichier destiné à être enregistré
		audioFile = new ArrayList<short[]>();

		//associe les actions aux boutons
        startButton = (Button) findViewById(R.id.startButton);
		startButton.setOnClickListener(new View.OnClickListener(){
			//action du bouton Start : commence l'enregistrement et l'affichage
			@Override
			public void onClick(View v){
				loadEngine();
				recorder.start(new OnBufferReadyListener() {
					@Override
					public void onBufferReady(short[] recordBuffer) {
						getTrunks(recordBuffer);
					}
				});

			}
		});
        stopButton = (Button) findViewById(R.id.stopButton);
		stopButton.setOnClickListener(new View.OnClickListener(){
			//action du bouton Stop : arrête l'enregistrement, stoppe le défilement, sauvegarde le fichier
			@Override
			public void onClick(View v){
				stopRecording();
				recorder.release();
				Log.d(RecordedSpectrogramActivity.TAG,String.valueOf(audioFile.size()));
				saveAudioFile(audioFile,"test"); //changer test par un nom (choisi par l'utilisateur?)
				audioFile.clear();
				Log.d(RecordedSpectrogramActivity.TAG,String.valueOf(audioFile.size()));
				//on l'a sauvegardé, plus besoin de le garder en cache
			}
		});
        viewButton = (Button) findViewById(R.id.viewButton);
		viewButton.setOnClickListener(new View.OnClickListener(){
			//action du bouton View : envoie sur l'activité RecordedSpectrogramActivity
			@Override
			public void onClick(View v){
				Intent intent = new Intent(getBaseContext() , RecordedSpectrogramActivity.class);
				startActivity(intent);

				}
		});

		replayButton = (Button) findViewById(R.id.replayButton);
		replayButton.setOnClickListener(new View.OnClickListener(){
		    @Override
			public void onClick(View v){
		    	audioFile = getAudioFile("test");
				Log.d(RecordedSpectrogramActivity.TAG,"lancement...");
				Log.d(RecordedSpectrogramActivity.TAG,"taille du fichier : "+String.valueOf(audioFile.size()));
				thread = new Thread(new Runnable() {
					@Override
					public void run() {
						processFile(audioFile);

					}
				});
				thread.start();
				Log.d("SpectrogramActivity","Service started");

			}

		});

		resetButton = (Button) findViewById(R.id.resetButton);
		resetButton.setOnClickListener(new View.OnClickListener(){
			@Override
			public void onClick(View v){
				Log.d("SpectrogramActivity","restart activity...");
				Intent intent = getIntent();
				overridePendingTransition(0,0);
				intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
				finish();
				startActivity(intent);
			}
		});


	}
	
	

	
	/**
	 * Control recording service
	 */
	private void startRecording() {
		recorder.start(new OnBufferReadyListener() {
			@Override
			public void onBufferReady(short[] recordBuffer) {
				getTrunks(recordBuffer);
			}
		});
	}
	private void stopRecording() {
		recorder.stop();
	}

    /**
     * Handles response to permission request
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_RECORD_AUDIO: {
                return;
            }
			case MY_PERMISSIONS_REQUEST_WRITE: {return;}

        }
    }

	/**
	 * Handles interactions with the menu
	 */
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.main_menu, menu);
		this.menu = menu;
		menu.findItem(R.id.action_bar_menu_play).setVisible(false);
		return true;
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		
		switch (item.getItemId()) {
		case R.id.action_bar_menu_settings:
			Intent intent = new Intent(this,PreferencesActivity.class);
			startActivityForResult(intent, INTENT_SETTINGS);
			return true;
		case R.id.action_bar_menu_play:
			menu.findItem(R.id.action_bar_menu_play).setVisible(false);
			menu.findItem(R.id.action_bar_menu_pause).setVisible(true);
			startRecording();
			return true;
		case R.id.action_bar_menu_pause:
			menu.findItem(R.id.action_bar_menu_pause).setVisible(false);
			menu.findItem(R.id.action_bar_menu_play).setVisible(true);
			stopRecording();
			return true;
		}
		return false;
	}
	
	/**
	 * Handles updates from the preference activity
	 */
	@Override
	protected void onActivityResult (int requestCode, int resultCode, Intent intent) {
	    //if (resultCode == Activity.RESULT_OK) {
	    	if (requestCode == INTENT_SETTINGS) {
	    		
	    		// Stop and release recorder if running
	    		recorder.stop();
	    		recorder.release();
	    		
	    		// Update preferences
	    		loadPreferences();
	    		
	    		// Notify view
	    		frequencyView.setFFTResolution(fftResolution);

                if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    loadEngine();
                }
	    		
	    		// Update color mode
	    		boolean nightMode = Misc.getPreference(this, "night_mode", false);
	            if (!nightMode)	{
	            	frequencyView.setBackgroundColor(Color.WHITE);
	            } else {
	            	frequencyView.setBackgroundColor(Color.BLACK);
	            }
	    	}
	    //}
	}
	
	@Override
	protected void onDestroy() {
		super.onDestroy();

		// Stop input streaming
		recorder.stop();
		recorder.release();
	}
	
	
	/**
	 * Make sure onDestroy is called
	 * NOTE: crash with illegalstateexception: no activity if this extends ActionBarActivity
	 */
	/*@Override
	public void onBackPressed() {
		Log.d("SpectrogramActivity","onBackPressed");
		onDestroy();
		finish();
	}*/
	


	/**
	 * Load preferences
	 */
	private void loadPreferences() {
		fftResolution = Integer.parseInt(Misc.getPreference(this, "fft_resolution", getString(R.string.preferences_fft_resolution_default_value)));
	}

	
	/**
	 * Initiates the recording service
	 * Creates objects to handle recording and FFT processing
	 */
	private void loadEngine() {
		
		// Stop and release recorder if running
		recorder.stop();
		recorder.release();
		
		// Prepare recorder
		recorder.prepare(fftResolution); // Record buffer size is forced to be a multiple of the fft resolution
		
		// Build buffers for runtime
		int n = fftResolution;
		fftBuffer = new short[n];
		re = new float[n];
		im = new float[n];
 		bufferStack = new ArrayList<short[]>();
		int l = recorder.getBufferLength()/(n/2);
		for (int i=0; i<l+1; i++) //+1 because the last one has to be used again and sent to first position
			bufferStack.add(new short[n/2]); // preallocate to avoid new within processing loop

        // Start recording
        startRecording();
        
		// Log
		//Log.d("recorder.getBufferLength()", recorder.getBufferLength()+" samples");
		//Log.d("bufferStack.size()", bufferStack.size()+" trunks");
	}
	
	
	/**
	 * Called every time the microphone record a sample
	 * Divide into smaller buffers (of size=resolution) which are overlapped by 50%
	 * Send these buffers for FFT processing (call to process())
	 */
	private void getTrunks(short[] recordBuffer) {
		int n = fftResolution;
		
		// Trunks are consecutive n/2 length samples		
		for (int i=0; i<bufferStack.size()-1; i++)
			System.arraycopy(recordBuffer, n/2*i, bufferStack.get(i+1), 0, n/2);
		
		// Build n length buffers for processing
		// Are build from consecutive trunks
		for (int i=0; i<bufferStack.size()-1; i++) {
			System.arraycopy(bufferStack.get(i), 0, fftBuffer, 0, n/2);
			System.arraycopy(bufferStack.get(i+1), 0, fftBuffer, n/2, n/2);
			process();
		}
		
		// Last item has not yet fully be used (only its first half)
		// Move it to first position in arraylist so that its last half is used
		short[] first = bufferStack.get(0);
		short[] last = bufferStack.get(bufferStack.size()-1);
		System.arraycopy(last, 0, first, 0, n/2);
	}
	
	/**
	 * Processes the sound waves
	 * Computes FFT
	 * Update views
	 */
	private void process() {
		int n = fftResolution;
		int log2_n = (int) (Math.log(n)/Math.log(2));
		audioFile.add(fftBuffer); //on stocke les buffers pour pouvoir rééxécuter la fft
		nativeLib.shortToFloat(fftBuffer, re, n);
		nativeLib.clearFloat(im, n);	// Clear imaginary part
		
		// Windowing to reduce spectrum leakage
		String window = Misc.getPreference(
				this,
				"window_type",
				getString(R.string.preferences_window_type_default_value));
		
		if (window.equals("Rectangular"))			nativeLib.windowRectangular(re, n);
		else if (window.equals("Triangular"))		nativeLib.windowTriangular(re, n);
		else if (window.equals("Welch"))			nativeLib.windowWelch(re, n);
		else if (window.equals("Hanning"))			nativeLib.windowHanning(re, n);
		else if (window.equals("Hamming"))			nativeLib.windowHamming(re, n);
		else if (window.equals("Blackman"))			nativeLib.windowBlackman(re, n);
		else if (window.equals("Nuttall"))			nativeLib.windowNuttall(re, n);
		else if (window.equals("Blackman-Nuttall"))	nativeLib.windowBlackmanNuttall(re, n);
		else if (window.equals("Blackman-Harris"))	nativeLib.windowBlackmanHarris(re, n);
		
		nativeLib.fft(re, im, log2_n, 0);	// Move into frquency domain 
		nativeLib.toPolar(re, im, n);	// Move to polar base

		frequencyView.setMagnitudes(re);
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				frequencyView.invalidate();
			}
		});
	}
	
	
	/**
	 * Switch visibility of the views as user click on view headers
	 */

	public void onFrequencyViewHeaderClick(View view) {
		System.out.println(frequencyView.getVisibility());
		if (frequencyView.getVisibility() == View.GONE)	frequencyView.setVisibility(View.VISIBLE);
		else											frequencyView.setVisibility(View.GONE);
	}



	/* Checks if external storage is available for read and write */
	public static boolean isExternalStorageWritable() {
		String state = Environment.getExternalStorageState();
		if (Environment.MEDIA_MOUNTED.equals(state)) {
			return true;
		}
		return false;
	}

	protected void saveAudioFile(ArrayList<short[]> audioFile,String filename) {
		String root = getFilesDir().getAbsolutePath();
		File file = new File(root+"/"+filename);
		try {
			file.createNewFile();
		} catch (IOException e) {
			e.printStackTrace();
		}
		ObjectOutputStream oos = null;
		try {
			oos = new ObjectOutputStream(new FileOutputStream(file));
		} catch (IOException e) {
			e.printStackTrace();
		}
		try {
			oos.writeObject(audioFile);
		} catch (IOException e) {
			e.printStackTrace();
		}
		try {
			oos.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	protected ArrayList<short[]> getAudioFile(String filename){
		String root = getFilesDir().getAbsolutePath();
		File file = new File(root+"/"+"test");
		ArrayList<short[]> result = new ArrayList<short[]>();
		ObjectInputStream objectInputStream = null;
		try {
			objectInputStream = new ObjectInputStream(new FileInputStream(file));
		} catch (IOException e) {
			e.printStackTrace();
		}
		try {
			 result = (ArrayList<short[]>) objectInputStream.readObject();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}
		return result;
	}

	protected void processBuffer(short[] buffer){
		int n = fftResolution;
		int log2_n = (int) (Math.log(n)/Math.log(2));
		nativeLib.shortToFloat(buffer, re, n);
		nativeLib.clearFloat(im, n);	// Clear imaginary part

		// Windowing to reduce spectrum leakage
		String window = Misc.getPreference(
				this,
				"window_type",
				getString(R.string.preferences_window_type_default_value));

		if (window.equals("Rectangular"))			nativeLib.windowRectangular(re, n);
		else if (window.equals("Triangular"))		nativeLib.windowTriangular(re, n);
		else if (window.equals("Welch"))			nativeLib.windowWelch(re, n);
		else if (window.equals("Hanning"))			nativeLib.windowHanning(re, n);
		else if (window.equals("Hamming"))			nativeLib.windowHamming(re, n);
		else if (window.equals("Blackman"))			nativeLib.windowBlackman(re, n);
		else if (window.equals("Nuttall"))			nativeLib.windowNuttall(re, n);
		else if (window.equals("Blackman-Nuttall"))	nativeLib.windowBlackmanNuttall(re, n);
		else if (window.equals("Blackman-Harris"))	nativeLib.windowBlackmanHarris(re, n);

		nativeLib.fft(re, im, log2_n, 0);	// Move into frquency domain
		nativeLib.toPolar(re, im, n);	// Move to polar base

		frequencyView.setMagnitudes(re);
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				frequencyView.invalidate();
			}
		});
	}

	protected void processFile(ArrayList<short[]> audioBuffers){
		ArrayList<short[]> tempCopy = (ArrayList<short[]>) audioBuffers.clone();
		Log.d("processing","taille du buffer :"+String.valueOf(tempCopy.size()));
		for (int i=0; i<tempCopy.size(); i++){
			Log.d("processing","process buffer");
			processBuffer(audioBuffers.get(i));}

	}

}
