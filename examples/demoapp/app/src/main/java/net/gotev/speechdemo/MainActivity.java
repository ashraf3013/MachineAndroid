package net.gotev.speechdemo;

import android.Manifest;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import com.android.volley.toolbox.Volley;
import com.android.volley.toolbox.StringRequest;
//import com.android.volley.RequestQueue;
//import com.android.volley.Request;
//import com.android.volley.Response;
import com.android.volley.*;
import android.content.*;
import org.json.*;
import android.util.Log;
import com.android.volley.toolbox.HttpHeaderParser;
import android.location.*;
import java.util.*;
//import net.gotev.speech.Client;
import java.net.*;
import java.io.*;
import com.tbruyelle.rxpermissions.RxPermissions;
import com.google.android.gms.location.*;
import com.google.android.gms.tasks.OnSuccessListener;

import net.gotev.speech.GoogleVoiceTypingDisabledException;
import net.gotev.speech.Speech;
import net.gotev.speech.SpeechDelegate;
import net.gotev.speech.SpeechRecognitionNotAvailable;
import net.gotev.speech.SpeechUtil;
import net.gotev.speech.TextToSpeechCallback;
import net.gotev.speech.ui.SpeechProgressView;
import net.gotev.toyproject.R;

import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity implements SpeechDelegate {


    protected LocationManager locationManager;
    Location location;
    private static final long MIN_DISTANCE_FOR_UPDATE = 10;
    private static final long MIN_TIME_FOR_UPDATE = 1000 * 60 * 2;



   /* public Location getLocation(String provider) {
        if (locationManager.isProviderEnabled(provider)) {
            locationManager.requestLocationUpdates(provider,MIN_TIME_FOR_UPDATE, MIN_DISTANCE_FOR_UPDATE, this);
            if (locationManager != null) {
                location = locationManager.getLastKnownLocation(provider);
                return location;
            }
        }
        return null;
    }*/

    Context mContext = this;

    public String getAddress(double lat, double lng) {
        Geocoder geocoder = new Geocoder(MainActivity.this, Locale.getDefault());
        String add=null;
        try {
            List<Address> addresses = geocoder.getFromLocation(lat, lng, 1);
            Address obj = addresses.get(0);
            add = obj.getAddressLine(0);
            add = add + "\n" + obj.getCountryName();
            add = add + "\n" + obj.getCountryCode();
            add = add + "\n" + obj.getAdminArea();
            add = add + "\n" + obj.getPostalCode();
            add = add + "\n" + obj.getSubAdminArea();
            add = add + "\n" + obj.getLocality();
            add = add + "\n" + obj.getSubThoroughfare();

            //Log.v("IGA", "Address" + add);
            // Toast.makeText(this, "Address=>" + add,
            // Toast.LENGTH_SHORT).show();

            // TennisAppActivity.showDialog(add);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show();
        }
        return add;
    }

    private ImageButton button;
    private Button speak;
    private TextView text;
    private EditText textToSpeech;
    private SpeechProgressView progress;
    private LinearLayout linearLayout;
    private FusedLocationProviderClient mFusedLocationClient;
    public int timeToRefresh=1;
    public Timer timer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        linearLayout = (LinearLayout) findViewById(R.id.linearLayout);

        button = (ImageButton) findViewById(R.id.button);
        button.setOnClickListener(view -> onButtonClick());

        speak = (Button) findViewById(R.id.speak);
        speak.setOnClickListener(view -> onSpeakClick());

        text = (TextView) findViewById(R.id.text);
        textToSpeech = (EditText) findViewById(R.id.textToSpeech) ;
        progress = (SpeechProgressView) findViewById(R.id.progress);

        int[] colors = {
                ContextCompat.getColor(this, android.R.color.black),
                ContextCompat.getColor(this, android.R.color.darker_gray),
                ContextCompat.getColor(this, android.R.color.black),
                ContextCompat.getColor(this, android.R.color.holo_orange_dark),
                ContextCompat.getColor(this, android.R.color.holo_red_dark)
        };
        progress.setColors(colors);
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);


        timer = new Timer();
        TimerTask hourlyTask = new TimerTask() {
            @Override
            public void run() {
                sayLocationV2();
            }
        };
        timer.schedule(hourlyTask, 0l, 1000 * timeToRefresh * 60);
    }

    public void sayLocationV2() {
        try {
            mFusedLocationClient.getLastLocation()
                    .addOnSuccessListener(this, new OnSuccessListener<Location>() {
                        @Override
                        public void onSuccess(Location location) {
                            // Got last known location. In some rare situations this can be null.
                            if (location != null) {
                                Toast.makeText(MainActivity.this, Double.toString(location.getLatitude()) + "  " + Double.toString(location.getLongitude()), Toast.LENGTH_SHORT).show();
                                //text.setText(getAddress(location.getLatitude(), location.getLongitude()));
                                Speech.getInstance().say(getAddress(location.getLatitude(), location.getLongitude()));
                            } else {
                                text.setText("location null");
                            }
                        }
                    });
        } catch (SecurityException e) {
            text.setText("exception while fetching location");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Speech.getInstance().shutdown();
    }

    private void onButtonClick() {
        if (Speech.getInstance().isListening()) {
            Speech.getInstance().stopListening();
        } else {
            RxPermissions.getInstance(this)
                    .request(Manifest.permission.RECORD_AUDIO)
                    .subscribe(granted -> {
                        if (granted) { // Always true pre-M
                            onRecordAudioPermissionGranted();
                        } else {
                            Toast.makeText(MainActivity.this, R.string.permission_required, Toast.LENGTH_LONG);
                        }
                    });
        }
    }

    private void onRecordAudioPermissionGranted() {
        button.setVisibility(View.GONE);
        linearLayout.setVisibility(View.VISIBLE);

        try {
            Speech.getInstance().stopTextToSpeech();
            Speech.getInstance().startListening(progress, MainActivity.this);

        } catch (SpeechRecognitionNotAvailable exc) {
            showSpeechNotSupportedDialog();

        } catch (GoogleVoiceTypingDisabledException exc) {
            showEnableGoogleVoiceTyping();
        }
    }

    private void onSpeakClick() {
        if (textToSpeech.getText().toString().trim().isEmpty()) {
            Toast.makeText(this, R.string.input_something, Toast.LENGTH_LONG).show();
            return;
        }

        Speech.getInstance().say(textToSpeech.getText().toString().trim(), new TextToSpeechCallback() {
            @Override
            public void onStart() {
                Toast.makeText(MainActivity.this, "TTS onStart", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onCompleted() {
                Toast.makeText(MainActivity.this, "TTS onCompleted", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError() {
                Toast.makeText(MainActivity.this, "TTS onError", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onStartOfSpeech() {
    }

    @Override
    public void onSpeechRmsChanged(float value) {
        //Log.d(getClass().getSimpleName(), "Speech recognition rms is now " + value +  "dB");
    }


    @Override
    public void onSpeechResult(String result) {

        button.setVisibility(View.VISIBLE);
        linearLayout.setVisibility(View.GONE);


        try {
        RequestQueue queue = Volley.newRequestQueue(this);
        String url ="http://powerful-dawn-75115.herokuapp.com/speech-to-text";
        //JSONObject jsonBody = new JSONObject();
       // jsonBody.put("body", result);
        //jsonBody.put("Author", "BNK");
        //final String requestBody = jsonBody.toString();
            final String requestBody = result;
            if("my current location".equalsIgnoreCase(requestBody)){
                //tellLocation();
                sayLocationV2();
            }else if(requestBody.contains("change timing to")){
                    try {
                        int timeExtracted = Integer.parseInt(requestBody.substring(17));
                        text.setText("time set to "+requestBody.substring(17));
                        //this.timeToRefresh=timeExtracted;
                        timer.cancel();
                        timer = new Timer();
                        TimerTask hourlyTask = new TimerTask() {
                            @Override
                            public void run() {
                                sayLocationV2();
                            }
                        };
                        timer.schedule(hourlyTask, 0l, 1000 * timeExtracted * 60);
                        //t = new Timer();
                        //TimerTask newTask = new MyTimerTask();  // new instance
                        //t.schedule(newTask, timeDelay);
                        Speech.getInstance().say("Okay setting time to "+requestBody.substring(17));
                    }catch(Exception ex){
                        text.setText("Exception while setting time");
                    }

            }else {

                StringRequest stringRequest = new StringRequest(Request.Method.POST, url, new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        //Toast.makeText(MainActivity.this, "onResponse", Toast.LENGTH_SHORT).show();
                        if ("ERR: No Reply Matched".equals(response)) {
                            Speech.getInstance().say("Sorry, I am not programmed to answer that");
                            text.setText("Sorry, I am not programmed to answer that");
                        } else {
                            Speech.getInstance().say(response);
                            text.setText("Response is: " + response);
                            //text.setText("Response is: "+ response.substring(0,500));
                            //Log.i("VOLLEY", response);
                            //onRecordAudioPermissionGranted();
                        }
                    }
                }, new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        text.setText("That didn't work!");
                        //onRecordAudioPermissionGranted();
                        //Log.e("VOLLEY", error.toString());
                    }
                }) {
                    @Override
                    public String getBodyContentType() {
                        return "application/json; charset=utf-8";
                    }

                    @Override
                    public byte[] getBody() throws AuthFailureError {
                        try {
                            return requestBody == null ? null : requestBody.getBytes("utf-8");
                        } catch (UnsupportedEncodingException uee) {
                            //VolleyLog.wtf("Unsupported Encoding while trying to get the bytes of %s using %s", requestBody, "utf-8");
                            Toast.makeText(MainActivity.this, "UnsupportedEncodingException", Toast.LENGTH_SHORT).show();
                            return null;
                        }
                    }

               /* @Override
                protected Response<String> parseNetworkResponse(NetworkResponse response) {
                    String responseString = "";
                    if (response != null) {
                        responseString = String.valueOf(response.statusCode);
                        // can get more details such as response.headers
                    }
                    return Response.success(responseString, HttpHeaderParser.parseCacheHeaders(response));
                }*/
                };

                //requestQueue.add(stringRequest);
// Add the request to the RequestQueue.
                queue.add(stringRequest);
            }
        } catch (Exception e) {
            Toast.makeText(MainActivity.this, "Exception", Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
        //text.setText(result);

        if (result.isEmpty()) {
            Speech.getInstance().say(getString(R.string.repeat));
            //onRecordAudioPermissionGranted();

        } else {
            //Speech.getInstance().say(result);
        }

    }

    @Override
    public void onSpeechPartialResults(List<String> results) {
        text.setText("");
        for (String partial : results) {
            text.append(partial + " ");
        }
    }

    private void showSpeechNotSupportedDialog() {
        DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                switch (which){
                    case DialogInterface.BUTTON_POSITIVE:
                        SpeechUtil.redirectUserToGoogleAppOnPlayStore(MainActivity.this);
                        break;

                    case DialogInterface.BUTTON_NEGATIVE:
                        break;
                }
            }
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(R.string.speech_not_available)
                .setCancelable(false)
                .setPositiveButton(R.string.yes, dialogClickListener)
                .setNegativeButton(R.string.no, dialogClickListener)
                .show();
    }
    private void showEnableGoogleVoiceTyping() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(R.string.enable_google_voice_typing)
                .setCancelable(false)
                .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        // do nothing
                    }
                })
                .show();
    }

}
