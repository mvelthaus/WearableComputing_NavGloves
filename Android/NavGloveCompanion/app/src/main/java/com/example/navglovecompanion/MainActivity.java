package com.example.navglovecompanion;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import org.w3c.dom.Text;

import java.net.URI;
import java.net.URL;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    final String TAG = "MainActivity";
    TextView outputField;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        outputField = (TextView) findViewById(R.id.outputField);

        Intent intent = getIntent();
        String action = intent.getAction();
        String type = intent.getType();

        /* spezifisch für den Empfang von Intents aus HERE Maps
        * Die Location befindet sich im Link, daher beim https splitten und den Link per Uri parsen lassen (handleIntent())
        * Aus dem String path muss dann noch latitude und logitude extrahiert werden (bisher nicht implementiert)
        */
        if (Intent.ACTION_SEND.equals(action) && type != null) {
            String sharedData = intent.getStringExtra(Intent.EXTRA_TEXT);
            String[] url = sharedData.split("https://");

            outputField.setText(handleIntent("https://"+url[1]));
            //Log.i(TAG, sharedData);
            //Log.i(TAG, url[1]);
        }
    }

    private String handleIntent(String data){
        Uri uri = Uri.parse(data);
        String protocol = uri.getScheme();
        String server = uri.getAuthority();
        String path = uri.getPath();
        Set<String> args = uri.getQueryParameterNames();

        Log.i("Protocol", protocol);
        Log.i("Server", server);
        Log.i("Path", path);

        for(String arg: args) {
            Log.i("Args", arg);
        }
        return path;
    }
}
