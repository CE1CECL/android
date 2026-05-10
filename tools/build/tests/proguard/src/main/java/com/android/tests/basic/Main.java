package com.android.tests.basic;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

public class Main extends Activity
{

    private int foo = 1234;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        TextView tv = (TextView) findViewById(R.id.dateText);
        StringProvider stringProvider = new StringProvider();
        tv.setText(stringProvider.getString(foo));
    }
}
