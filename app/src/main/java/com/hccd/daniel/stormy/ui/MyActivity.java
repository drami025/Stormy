package com.hccd.daniel.stormy.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.hccd.daniel.stormy.R;
import com.hccd.daniel.stormy.weather.Current;
import com.hccd.daniel.stormy.weather.Day;
import com.hccd.daniel.stormy.weather.Forecast;
import com.hccd.daniel.stormy.weather.Hour;
import com.squareup.okhttp.Call;
import com.squareup.okhttp.Callback;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.List;

import butterknife.ButterKnife;
import butterknife.InjectView;
import butterknife.OnClick;


public class MyActivity extends Activity implements LocationListener{

    public static final String TAG = "MainActivity.class.get";
    public static final String DAILY_FORECAST = "DAILY_FORECAST";

    private Forecast mForecast;
    private LocationManager mLocationManager;

    @InjectView(R.id.temperatureLabel) TextView mTemperatureLabel;
    @InjectView(R.id.timeLabel) TextView mTimeLabel;
    @InjectView(R.id.humidityValue) TextView mHumidityValue;
    @InjectView(R.id.precipValue) TextView mPrecipValue;
    @InjectView(R.id.iconImageView) ImageView mIconImageView;
    @InjectView(R.id.summaryLabel) TextView mSummaryLabel;
    @InjectView(R.id.refreshImageView) ImageView mRefreshImageView;
    @InjectView(R.id.progressBar) ProgressBar mProgressBar;
    @InjectView(R.id.currentLocationLabel) TextView mCurrentLocationLabel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my);
        ButterKnife.inject(this);

        mProgressBar.setVisibility(View.INVISIBLE);

        mLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        boolean enabled = mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);

        mRefreshImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String provider = mLocationManager.getBestProvider(new Criteria(), false);
                mLocationManager.requestSingleUpdate(provider, MyActivity.this, null);
            }
        });

        if(enabled || isNetworkAvailable()) {
            Location location = getLastKnownLocation();
            getCityAndState(location.getLatitude(), location.getLongitude());
            getForecast(location.getLatitude(), location.getLongitude());
        }
        else{
            alertUserAboutGPS();
        }

        Log.d(TAG, "Main UI code is running.");
    }

    private void getForecast(double latitude, double longitude) {
        String apiKey = "04c2e9e66380236b92d0ddc499aa9121";
        String forecastUrl = "https://api.forecast.io/forecast/" + apiKey + "/" +
                latitude + "," + longitude;


        if(isNetworkAvailable()) {
            toggleRefresh();

            OkHttpClient client = new OkHttpClient();
            Request request = new Request.Builder()
                    .url(forecastUrl)
                    .build();

            Call call = client.newCall(request);
            call.enqueue(new Callback() {
                @Override
                public void onFailure(Request request, IOException e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            toggleRefresh();
                        }
                    });
                    alertUserAboutError();
                }

                @Override
                public void onResponse(Response response) throws IOException {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            toggleRefresh();
                        }
                    });

                    try {
                        String jsonData = response.body().string();
                        Log.v(TAG, jsonData);
                        if (response.isSuccessful()) {
                            mForecast = parseForecastDetails(jsonData);
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    updateDisplay();
                                }
                            });

                        } else {
                            alertUserAboutError();
                        }
                    } catch (IOException e) {
                        Log.e(TAG, "Exception caught", e);
                    } catch (JSONException e){
                        Log.e(TAG, "Json exception", e);
                    }
                }
            });
        }
        else{
            Toast.makeText(this, getString(R.string.network_unavailable_message),
                    Toast.LENGTH_LONG).show();
        }
    }

    private void getCityAndState(double latitude, double longitude){
        String mapsUrl = "http://maps.googleapis.com/maps/api/geocode/json?latlng="
                + latitude + "," + longitude;

        if(isNetworkAvailable()){
            OkHttpClient client = new OkHttpClient();
            Request request = new Request.Builder()
                    .url(mapsUrl)
                    .build();

            Call call = client.newCall(request);
            call.enqueue(new Callback() {
                @Override
                public void onFailure(Request request, IOException e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            String unavailable_loc = MyActivity.this.getResources()
                                    .getResourceName(R.string.unavailable_location);
                            mCurrentLocationLabel.setText(unavailable_loc);
                        }
                    });
                }

                @Override
                public void onResponse(Response response) throws IOException {
                    String jsonData = response.body().string();
                    if(response.isSuccessful()){
                        try {
                            final String cityState = parseLocationDetails(jsonData);
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    mCurrentLocationLabel.setText(cityState);
                                }
                            });
                        }
                        catch (JSONException e){
                            alertUserAboutError();
                        }
                    }
                    else{
                        alertUserAboutError();
                    }
                }
            });
        }
    }

    private String parseLocationDetails(String jsonData) throws  JSONException{
        JSONObject location = new JSONObject(jsonData);
        JSONArray results = location.getJSONArray("results");
        JSONObject firstResult = results.getJSONObject(0);
        JSONArray addressComponents = firstResult.getJSONArray("address_components");

        String city = null;
        String state = null;

        for(int i = 0; i < addressComponents.length(); i++){
            JSONObject obj = addressComponents.getJSONObject(i);
            JSONArray types = obj.getJSONArray("types");
            String t = "";
            for(int j = 0; j < types.length(); j++){
                t = types.getString(j);
                if(t.equals("neighborhood") || t.equals("administrative_area_level_1")){
                    break;
                }
            }

            if(t.equals("neighborhood")){
                city = obj.getString("long_name");
            }
            else if(t.equals("administrative_area_level_1")){
                state = obj.getString("short_name");
            }

            if(city != null && state != null){
                break;
            }
        }

        return city + ", " + state;
    }

    private void toggleRefresh() {

        if(mProgressBar.getVisibility() == View.INVISIBLE) {
            mProgressBar.setVisibility(View.VISIBLE);
            mRefreshImageView.setVisibility(View.INVISIBLE);
        }
        else{
            mProgressBar.setVisibility(View.INVISIBLE);
            mRefreshImageView.setVisibility(View.VISIBLE);
        }
    }

    private void updateDisplay() {
        Current current = mForecast.getCurrent();
        mTemperatureLabel.setText(current.getTemperature() + "");
        mTimeLabel.setText("At " + mForecast.getCurrent().getFormattedTime() + " it will be");
        mHumidityValue.setText(current.getHumidity() + "");
        mPrecipValue.setText(current.getPrecipChance() + "%");
        mSummaryLabel.setText(current.getSummary());

        Drawable drawable = getResources().getDrawable(current.getIconId());
        mIconImageView.setImageDrawable(drawable);
    }

    private Forecast parseForecastDetails(String jsonData) throws JSONException{
        Forecast forecast = new Forecast();

        forecast.setCurrent(getCurrentDetails(jsonData));
        forecast.setHourlyForecast(getHourlyForecast(jsonData));
        forecast.setDailyForecast(getDailyForecast(jsonData));

        return forecast;
    }

    private Day[] getDailyForecast(String jsonData) throws JSONException {
        JSONObject forecast = new JSONObject(jsonData);
        String timezone = forecast.getString("timezone");
        JSONObject daily =  forecast.getJSONObject("daily");
        JSONArray data = daily.getJSONArray("data");

        Day[] days = new Day[data.length()];

        for(int i = 0; i < data.length(); i++){
            JSONObject jsonDay = data.getJSONObject(i);
            Day day = new Day();

            day.setSummary(jsonDay.getString("summary"));
            day.setTimezone(timezone);
            day.setTime(jsonDay.getLong("time"));
            day.setIcon(jsonDay.getString("icon"));
            day.setTemperatureMax(jsonDay.getDouble("temperatureMax"));

            days[i] = day;
        }

        return days;
    }

    private Hour[] getHourlyForecast(String jsonData) throws JSONException{
        JSONObject forecast = new JSONObject(jsonData);
        String timezone = forecast.getString("timezone");
        JSONObject hourly = forecast.getJSONObject("hourly");
        JSONArray data = hourly.getJSONArray("data");

        Hour[] hours = new Hour[data.length()];

        for(int i = 0; i < data.length(); i++){
            JSONObject jsonHour = data.getJSONObject(i);
            Hour hour = new Hour();

            hour.setSummary(jsonHour.getString("summary"));
            hour.setIcon(jsonHour.getString("icon"));
            hour.setTemperature(jsonHour.getDouble("temperature"));
            hour.setTime(jsonHour.getLong("time"));
            hour.setTimezone(timezone);

            hours[i] = hour;
        }

        return hours;
    }

    private Current getCurrentDetails(String jsonData) throws JSONException {
        JSONObject forecast = new JSONObject(jsonData);
        String timezone = forecast.getString("timezone");
        Log.i(TAG, "From JSON: " + timezone);

        JSONObject currently = forecast.getJSONObject("currently");

        Current current = new Current();

        current.setHumidity(currently.getDouble("humidity"));
        current.setPrecipChance(currently.getDouble("precipProbability"));
        current.setTemperature(currently.getDouble("temperature"));
        current.setTime(currently.getLong("time"));
        current.setSummary(currently.getString("summary"));
        current.setIcon(currently.getString("icon"));
        current.setTimeZone(timezone);
        Log.d(TAG, current.getFormattedTime());

        return current;
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager manager = (ConnectivityManager)
                getSystemService(Context.CONNECTIVITY_SERVICE);

        NetworkInfo networkInfo = manager.getActiveNetworkInfo();
        boolean isAvailable = false;

        if(networkInfo != null && networkInfo.isConnected()){
            isAvailable = true;
        }
        return isAvailable;
    }

    private void alertUserAboutError() {
        AlertDialogFragment dialog = new AlertDialogFragment();
        dialog.show(getFragmentManager(), "error_dialog");
    }

    private void alertUserAboutGPS(){
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle("GPS not available")
                .setMessage("Please turn on GPS in your settings")
                .setPositiveButton("Settings", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Intent intent = new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                        startActivity(intent);
                    }
                })
                .setNegativeButton("Close app", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        finish();
                        System.exit(0);
                    }
                });

        builder.create().show();
    }

    private Location getLastKnownLocation() {
        mLocationManager = (LocationManager)getApplicationContext().getSystemService(LOCATION_SERVICE);
        List<String> providers = mLocationManager.getProviders(true);
        Location bestLocation = null;
        for (String provider : providers) {
            Location l = mLocationManager.getLastKnownLocation(provider);
            if (l == null) {
                continue;
            }
            if (bestLocation == null || l.getAccuracy() < bestLocation.getAccuracy()) {
                // Found best last known location: %s", l);
                bestLocation = l;
            }
        }
        return bestLocation;
    }


    @OnClick (R.id.dailyButton)
    public void startDailyActivity(View view){
        Intent intent = new Intent(this, DailyForecastActivity.class);
        intent.putExtra(DAILY_FORECAST, mForecast.getDailyForecast());
        startActivity(intent);
    }

    @Override
    public void onLocationChanged(Location location) {
        getForecast(location.getLatitude(), location.getLongitude());
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {

    }

    @Override
    public void onProviderEnabled(String provider) {

    }

    @Override
    public void onProviderDisabled(String provider) {

    }

    @Override
    public void onResume(){
        Location location = getLastKnownLocation();
        getCityAndState(location.getLatitude(), location.getLongitude());
        getForecast(location.getLatitude(), location.getLongitude());
        super.onResume();
    }
}
