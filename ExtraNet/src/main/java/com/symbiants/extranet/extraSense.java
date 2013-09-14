package com.symbiants.extranet;

import android.os.Bundle;
import android.app.Activity;
import android.view.Menu;

public class extraSense extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.network_layout);
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.extra_sense, menu);
        return true;
    }
    
}
