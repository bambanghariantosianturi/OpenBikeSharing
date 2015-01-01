/*
 * Copyright (c) 2014-2015 Bruno Parmentier. This file is part of OpenBikeSharing.
 *
 * OpenBikeSharing is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * OpenBikeSharing is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with OpenBikeSharing.  If not, see <http://www.gnu.org/licenses/>.
 */

package be.brunoparmentier.openbikesharing.app.ui;

import android.app.ActionBar;
import android.app.AlertDialog;
import android.app.FragmentTransaction;
import android.app.SearchManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.MatrixCursor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.SearchView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import be.brunoparmentier.openbikesharing.app.BikeNetwork;
import be.brunoparmentier.openbikesharing.app.R;
import be.brunoparmentier.openbikesharing.app.SearchStationAdapter;
import be.brunoparmentier.openbikesharing.app.Station;
import be.brunoparmentier.openbikesharing.app.fragments.StationsListFragment;
import be.brunoparmentier.openbikesharing.app.utils.OBSException;
import be.brunoparmentier.openbikesharing.app.utils.parser.BikeNetworkParser;


public class StationsListActivity extends FragmentActivity implements ActionBar.TabListener {
    private final String BASE_URL = "http://api.citybik.es/v2/networks";
    private final String PREF_NETWORK_ID_LABEL = "network-id";
    private final String PREF_FAV_STATIONS = "fav-stations";
    private final String TAG = "StationsListActivity";
    private SharedPreferences settings;
    private BikeNetwork bikeNetwork;
    private ArrayList<Station> stations;
    private ArrayList<Station> favStations;
    private boolean firstRun;

    private Menu optionsMenu;

    private ViewPager viewPager;
    private TabsPagerAdapter tabsPagerAdapter;

    private StationsListFragment allStationsFragment;
    private StationsListFragment favoriteStationsFragment;

    private ActionBar actionBar;
    private SearchView searchView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stations_list);

        viewPager = (ViewPager) findViewById(R.id.viewPager);
        viewPager.setOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

            }

            @Override
            public void onPageSelected(int position) {
                actionBar.setSelectedNavigationItem(position);
            }

            @Override
            public void onPageScrollStateChanged(int state) {

            }
        });

        actionBar = getActionBar();
        actionBar.addTab(actionBar.newTab()
                .setText(getString(R.string.all_stations))
                .setTabListener(this));
        actionBar.addTab(actionBar.newTab()
                .setText(getString(R.string.favorite_stations))
                .setTabListener(this));
        actionBar.setHomeButtonEnabled(false);

        settings = PreferenceManager.getDefaultSharedPreferences(this);
        firstRun = settings.getString(PREF_NETWORK_ID_LABEL, "").isEmpty();

        if (firstRun) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage(R.string.welcome_dialog_message);
            builder.setTitle(R.string.welcome_dialog_title);
            builder.setPositiveButton(R.string.welcome_dialog_ok, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    Intent intent = new Intent(StationsListActivity.this, SettingsActivity.class);
                    startActivity(intent);
                }
            });
            builder.setNegativeButton(R.string.welcome_dialog_cancel, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    finish();
                }
            });
            AlertDialog dialog = builder.create();
            dialog.show();
        } else {
            if (savedInstanceState != null) {
                bikeNetwork = (BikeNetwork) savedInstanceState.getSerializable("bikeNetwork");
                stations = (ArrayList<Station>) savedInstanceState.getSerializable("stations");
                favStations = (ArrayList<Station>) savedInstanceState.getSerializable("favStations");
                actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
                tabsPagerAdapter = new TabsPagerAdapter(getSupportFragmentManager());
                viewPager.setAdapter(tabsPagerAdapter);
            } else {
                String stationUrl = BASE_URL + "/" + settings.getString(PREF_NETWORK_ID_LABEL, "");
                new JSONDownloadTask().execute(stationUrl);
            }
        }
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        if (tabsPagerAdapter == null) {
            String stationUrl = BASE_URL + "/" + settings.getString(PREF_NETWORK_ID_LABEL, "");
            new JSONDownloadTask().execute(stationUrl);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable("bikeNetwork", bikeNetwork);
        outState.putSerializable("stations", stations);
        outState.putSerializable("favStations", favStations);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        this.optionsMenu = menu;
        getMenuInflater().inflate(R.menu.stations_list, menu);

        SearchManager manager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);

        searchView = (SearchView) menu.findItem(R.id.action_search).getActionView();
        searchView.setSearchableInfo(manager.getSearchableInfo(getComponentName()));
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {

            @Override
            public boolean onQueryTextSubmit(String s) {
                return false;
            }

            @Override
            public boolean onQueryTextChange(String s) {
                loadData(s);
                return true;
            }
        });

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        switch (id) {
            case R.id.action_settings:
                Intent settingsIntent = new Intent(this, SettingsActivity.class);
                startActivity(settingsIntent);
                return true;
            case R.id.action_refresh:
                String networkId = PreferenceManager
                        .getDefaultSharedPreferences(this)
                        .getString(PREF_NETWORK_ID_LABEL, "");
                new JSONDownloadTask().execute(BASE_URL + "/" + networkId);
                return true;
            case R.id.action_map:
                if (bikeNetwork != null) {
                    Intent mapIntent = new Intent(this, MapActivity.class);
                    mapIntent.putExtra("bike-network", bikeNetwork);
                    startActivity(mapIntent);
                } else {
                    Toast.makeText(this,
                            R.string.bike_network_downloading,
                            Toast.LENGTH_LONG).show();
                }
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onTabSelected(ActionBar.Tab tab, FragmentTransaction fragmentTransaction) {
        viewPager.setCurrentItem(tab.getPosition());
    }

    @Override
    public void onTabUnselected(ActionBar.Tab tab, FragmentTransaction fragmentTransaction) {

    }

    @Override
    public void onTabReselected(ActionBar.Tab tab, FragmentTransaction fragmentTransaction) {

    }

    private void loadData(String query) {
        ArrayList<Station> queryStations = new ArrayList<Station>();
        String[] columns = new String[]{"_id", "text"};
        Object[] temp = new Object[]{0, "default"};

        MatrixCursor cursor = new MatrixCursor(columns);

        if (stations != null) {
            for (int i = 0; i < stations.size(); i++) {
                Station station = stations.get(i);
                if (station.getName().toLowerCase().contains(query.toLowerCase())) {
                    temp[0] = i;
                    temp[1] = station.getName();
                    cursor.addRow(temp);
                    queryStations.add(station);
                }
            }
        }

        searchView.setSuggestionsAdapter(new SearchStationAdapter(this, cursor, queryStations));

    }

    private void setRefreshActionButtonState(final boolean refreshing) {
        if (optionsMenu != null) {
            final MenuItem refreshItem = optionsMenu.findItem(R.id.action_refresh);
            if (refreshItem != null) {
                if (refreshing) {
                    refreshItem.setActionView(R.layout.actionbar_indeterminate_progress);
                    refreshItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
                } else {
                    refreshItem.setActionView(null);
                    refreshItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
                }
            }
        }
    }

    private class JSONDownloadTask extends AsyncTask<String, Void, String> {

        Exception error;

        @Override
        protected void onPreExecute() {
            setRefreshActionButtonState(true);
        }

        @Override
        protected String doInBackground(String... urls) {
            if (urls[0].isEmpty()) {
                finish();
            }
            try {
                StringBuilder response = new StringBuilder();

                URL url = new URL(urls[0]);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    BufferedReader input = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    String strLine;
                    while ((strLine = input.readLine()) != null) {
                        response.append(strLine);
                    }
                    input.close();
                }
                Log.d(TAG, "Stations downloaded");
                return response.toString();
            } catch (IOException e) {
                error = e;
                Log.d(TAG, e.getMessage());
                return e.getMessage();
            }
        }

        @Override
        protected void onPostExecute(String result) {
            if (error != null) {
                Toast.makeText(getApplicationContext(),
                        getApplicationContext().getResources().getString(R.string.connection_error),
                        Toast.LENGTH_SHORT).show();
                setRefreshActionButtonState(false);
            } else {
                try {
                    JSONObject jsonObject = new JSONObject(result);

                    /* parse result */
                    boolean stripId = settings.getBoolean("pref_strip_id_station", false);
                    BikeNetworkParser bikeNetworkParser = new BikeNetworkParser(jsonObject, stripId);
                    bikeNetwork = bikeNetworkParser.getNetwork();
                    stations = bikeNetwork.getStations();

                    Collections.sort(stations);

                    /* create new list with favorite stations */
                    Set<String> favorites = settings.getStringSet(PREF_FAV_STATIONS, new HashSet<String>());
                    favStations = new ArrayList<>();
                    for (Station station : stations) {
                        if (favorites.contains(station.getId())) {
                            favStations.add(station);
                        }
                    }

                    if (tabsPagerAdapter != null) {
                        tabsPagerAdapter.updateAllStationsListFragment(stations);
                        tabsPagerAdapter.updateFavoriteStationsFragment(favStations);
                    } else {
                        actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
                        tabsPagerAdapter = new TabsPagerAdapter(getSupportFragmentManager());
                        viewPager.setAdapter(tabsPagerAdapter);
                    }
                } catch (JSONException | OBSException e) {
                    Toast.makeText(StationsListActivity.this,
                            e.getMessage(), Toast.LENGTH_LONG).show();
                } finally {
                    setRefreshActionButtonState(false);
                }
            }
        }
    }

    private class TabsPagerAdapter extends FragmentPagerAdapter {
        private final int NUM_ITEMS = 2;

        public TabsPagerAdapter(FragmentManager fragmentManager) {
            super(fragmentManager);

            allStationsFragment = StationsListFragment.newInstance(stations);
            favoriteStationsFragment = StationsListFragment.newInstance(favStations);
        }

        @Override
        public int getCount() {
            return NUM_ITEMS;
        }

        @Override
        public Fragment getItem(int position) {
            switch (position) {
                case 0:
                    return allStationsFragment;
                case 1:
                    return favoriteStationsFragment;
                default:
                    return null;
            }
        }

        public void updateAllStationsListFragment(ArrayList<Station> stations) {
            allStationsFragment.updateStationsList(stations);
        }

        public void updateFavoriteStationsFragment(ArrayList<Station> stations) {
            favoriteStationsFragment.updateStationsList(stations);
        }
    }
}
