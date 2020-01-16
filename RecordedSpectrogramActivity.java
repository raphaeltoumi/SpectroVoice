/*
Activité lancée après avoir choisi un des fichiers sauvegardés, permet d'afficher sa fft
 */
package net.galmiza.android.spectrogram;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import net.galmiza.android.engine.sound.SoundEngine;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

public class RecordedSpectrogramActivity extends AppCompatActivity {

    // Constant
    static final float PI = (float) Math.PI;
    static final int INTENT_SETTINGS = 0;
    static final int MY_PERMISSIONS_REQUEST_WRITE = 1;
    static final String TAG = "RecordedSpectrogramActivity";

    // Attributes
    private FrequencyView frequencyView;
    private SoundEngine nativeLib;
    private Menu menu;
    private int samplingRate = 44100;
    private int fftResolution;
    private ArrayList<short[]> audioFile;
    private Button playButton,backButton;

    // Buffers
    private short[] fftBuffer; // buffer supporting the fft process
    private float[] re; // buffer holding real part during fft process
    private float[] im; // buffer holding imaginary part during fft process
    private String filename = "test";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.recorded);

        // Share core
       Misc.setAttribute("activity", this);

       // Load preferences
       loadPreferences();

       // JNI interface
       nativeLib = new SoundEngine();
       nativeLib.initFSin();

       audioFile = new ArrayList<short[]>();

       frequencyView = (FrequencyView) findViewById(R.id.frequency_view1);
       if (Misc.getPreference(this, "keep_screen_on", false))
           frequencyView.setKeepScreenOn(true);
       frequencyView.setFFTResolution(fftResolution);
       frequencyView.setSamplingRate(samplingRate);

       // Color mode
       boolean nightMode = Misc.getPreference(this, "night_mode", true);
       if (!nightMode) {
           frequencyView.setBackgroundColor(Color.WHITE);
       } else {
           frequencyView.setBackgroundColor(Color.BLACK);
       }

       // Prepare screen
       getSupportActionBar().hide();
       if (Misc.getPreference(this, "hide_status_bar", false)){
       	getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);}



        //Si on a eu la permission, on charge le fichier
       if ((ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED)&&SpectrogramActivity.isExternalStorageWritable()) {
           audioFile = getAudioFile("test");
       }
       else{
           ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, MY_PERMISSIONS_REQUEST_WRITE);
       }
       playButton = (Button) findViewById(R.id.playButton);
       playButton.setOnClickListener(new View.OnClickListener(){
           public void onClick(View v){
               Log.d(TAG,"lancement...");
               //on passe un à un les buffers stockés (le fichier) pour les traiter
               fftBuffer = new short[fftResolution];
               //test voir si le pb vient d'ici
               if (audioFile!=null){
                   Log.d(TAG,"fichier pas vide : lourd !");
                   Log.d(TAG, "taille :"+String.valueOf(audioFile.size()));
                   nativeLib.shortToFloat(audioFile.get(0),re,fftResolution);
                   frequencyView.setMagnitudes(re);
                   for (short[] buffer : audioFile) {
                       System.arraycopy(buffer, 0, fftBuffer, 0, fftBuffer.length);
                       process();
                   }}
               else {
                   Toast.makeText(getBaseContext(), "le fichier est vide", Toast.LENGTH_LONG).show();
               }
           }
       });

        backButton = (Button) findViewById(R.id.backButton);
        backButton.setOnClickListener(new View.OnClickListener(){
                                          public void onClick(View v){
                                              Log.d(TAG,"clicBack");
                                              Intent intent = new Intent(getBaseContext() , SpectrogramActivity.class);
                                              startActivity(intent);


                                          }
        });
    }


    /**
     * Update the text in frame headers
     */
   /*
   private void updateHeaders() {
       // Freqnecy view
       TextView frequency = (TextView) findViewById(R.id.textview_frequency_header);
       String window = Misc.getPreference(
               this,
               "window_type",
               getString(R.string.preferences_window_type_default_value));
       frequency.setText(String.format(
               getString(R.string.view_header_frequency), fftResolution, window));

       // Color
       boolean nightMode = Misc.getPreference(this, "night_mode", false);
       if (!nightMode) {
           frequency.setBackgroundColor(Color.LTGRAY);
           frequency.setTextColor(Color.BLACK);
       } else {
           frequency.setBackgroundColor(Color.DKGRAY);
           frequency.setTextColor(Color.WHITE);
       }
   } */

    /**
     * Handles response to permission request
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == MY_PERMISSIONS_REQUEST_WRITE) {
            for (int i = 0; i < permissions.length; i++) {
                String permission = permissions[i];
                int grantResult = grantResults[i];

                if (permission.equals(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                    if (grantResult == PackageManager.PERMISSION_GRANTED) {
                        //charge le fichier
                        File file = new File(Environment.getExternalStoragePublicDirectory(
                                Environment.DIRECTORY_PICTURES), filename);
                        ObjectInputStream objectInputStream = null;
                        try {
                            objectInputStream = new ObjectInputStream(new FileInputStream(file));
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        try {
                            audioFile = (ArrayList<short[]>) objectInputStream.readObject();
                        } catch (IOException e) {
                            e.printStackTrace();
                        } catch (ClassNotFoundException e) {
                            e.printStackTrace();
                        }
                        try {
                            objectInputStream.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    } else {
                        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, MY_PERMISSIONS_REQUEST_WRITE);
                    }
                }
            }
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
                return true;
            case R.id.action_bar_menu_pause:
                menu.findItem(R.id.action_bar_menu_pause).setVisible(false);
                menu.findItem(R.id.action_bar_menu_play).setVisible(true);
                return true;
        }
        return false;
    }



    @Override
    protected void onDestroy() {
        super.onDestroy();
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
     * Processes the sound waves
     * Computes FFT
     * Update views
     */
    private void process() {
        int n = fftResolution;
        int log2_n = (int) (Math.log(n)/Math.log(2));

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

    public File getAudioStorageDir(Context context, String audioName) {
        // Get the directory for the app's private pictures directory.
        File file = new File(context.getExternalFilesDir(
                Environment.DIRECTORY_MUSIC), audioName);
	/* if (!file.mkdirs()) {
		Log.e(LOG_TAG, "Directory not created");
	}*/
        return file;
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
}