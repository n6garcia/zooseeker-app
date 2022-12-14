package com.example.zooseeker_cse_110_team_30;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import android.app.AlertDialog;


import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * The directions activity for this application.
 * @see "https://developer.android.com/reference/android/app/Activity.html"
 */
public class DirectionsActivity extends AppCompatActivity {
    private TextView exhibitName; //large name of exhibit
    private TextView directionsText; //directions through park
    private TextView nextText; //next exhibit name + distance

    private Button previousButton; //back button
    private Button skipButton; //skip button
    private Button nextButton; //next button
    private Switch detailedSwitch; //detailed directions switch
    private Switch mockSwitch; // enable location mocking

    private List<Exhibit> visitHistory; //list of previously visited exhibits, in order
    private boolean detailedDirections; //whether or not to display detailed directions
    public boolean useMockLocation; //whether or not to use mock coordinates

    private static ExhibitDao dao; //exhibit database
    private Exhibit targetExhibit; //exhibit user is navigating to
    public Exhibit userCurrentExhibit; //exhibit user is closest to, aka the "current" exhibit
    public boolean replanPrompted; //whether or not a replan has been prompted for this exhibit
    public AlertDialog alertDialog; //replan alert popup
    public ExhibitViewModel viewModel; //manages UI data + handlers

    private LocationListener locationListener;
    private LocationManager locationManager;

    /**
     * Function that runs when this Activity is created. Set up most classes.
     * @param savedInstanceState Most recent Bundle data, otherwise null
     * @see "https://developer.android.com/reference/android/app/Activity#onCreate(android.os.Bundle)"
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_directions);

        //set up instance var textViews
        this.exhibitName = this.findViewById(R.id.exhibit_name);
        this.directionsText = this.findViewById(R.id.directions_text);
        this.nextText = this.findViewById(R.id.next_text);

        //get alert dialog for replan
        this.alertDialog = Utilities.getReplanAlert(this);
        this.viewModel =  new ViewModelProvider(this)
                .get(ExhibitViewModel.class); //get ExhibitViewModel from the provider

        // set up back button click
        this.previousButton = this.findViewById(R.id.previous_button); //get button from layout
        previousButton.setOnClickListener(this::onPreviousButtonClicked);

        // set up skip button click
        this.skipButton = this.findViewById(R.id.skip_button); //get button from layout
        skipButton.setOnClickListener(this::onSkipButtonClicked);

        // set up next button click
        this.nextButton = this.findViewById(R.id.next_button); //get button from layout
        nextButton.setOnClickListener(this::onNextButtonClicked);

        // set up detailed switch click
        this.detailedSwitch = this.findViewById(R.id.detailed_directions_switch);
        detailedSwitch.setOnClickListener(this::onDirectionsSwitchToggled);

        // set up mocking switch click
        this.mockSwitch = this.findViewById(R.id.mock_location_switch);
        mockSwitch.setOnClickListener(this::onMockButtonClicked);

        //accessing user location/detecting location change
        String provider = LocationManager.GPS_PROVIDER;
        this.locationManager = (LocationManager)this.getSystemService(Context.LOCATION_SERVICE);
        this.locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(@NonNull Location location) {
                locationChangedHandler(location); //call our location handler instead
            }
        };
        try {
            locationManager.requestLocationUpdates(provider, 0, 0f, locationListener);
        }
        catch(SecurityException e) { //throws an error if we don't handle this exception
            return;
        }

        //set instance variables
        dao = ExhibitDatabase.getSingleton(this.getApplicationContext()).exhibitDao();
        this.targetExhibit = dao.get("entrance_exit_gate"); //default values
        this.userCurrentExhibit = dao.get("entrance_exit_gate");
        this.replanPrompted = false;
        this.detailedDirections = false;

        this.visitHistory = new ArrayList<>(); //need ArrayList features

        //resumes visit plan if app killed while in middle of visit plan
        if(dao.getVisited().size() > 0) { //exhibits have been visited
            resumeVisitPlan(dao.getVisited());
            this.userCurrentExhibit = this.visitHistory.get(visitHistory.size() - 1);
            this.targetExhibit = Directions.getClosestUnvisitedExhibit(userCurrentExhibit);
            //need to go back 1 so onNextButtonClicked will go to right place
            this.onPreviousButtonClicked(previousButton.getRootView());
        }

        //we call this method because it handles all the next logic for us
        onNextButtonClicked(this.nextButton.getRootView()); //advance to first exhibit
    }

    /**
     * Utility method. Resumes visit plan if this activity is started with > 0 visited exhibits.
     * @param visitList the new visit history, in order.
     */
    private void resumeVisitPlan(List<Exhibit> visitList) {
        for(Exhibit exhibit : visitList) {
            this.visitHistory.add(exhibit); //can't just set visitHistory because we need ArrayList features
            exhibit.visited = visitHistory.size(); //in case something changed
            dao.update(exhibit);
        }
    }

    /**
     * Event handler for clicking the back button.
     * @param view The View which contains the button.
     */
    public void onPreviousButtonClicked(View view){
        //System.out.println(visitHistory.size());
        if(visitHistory.size() == 0) { //clicked back button on first exhibit (entrance)
            locationManager.removeUpdates(locationListener); //stop accessing location
          
            //System.out.println("empty");
            if(dao.getSelected().size() == 0) {
                finish();
                Intent mainIntent = new Intent(this, MainActivity.class);
                startActivity(mainIntent);
            }
            else {
                finish();
                Intent planIntent = new Intent(this, VisitPlanActivity.class);
                startActivity(planIntent);
            }
        }
        else { //need this to prevent crashing lmao
            //remove from visit history list, removed exhibit is the one we navigate back to
            Exhibit removedExhibit = visitHistory.remove(visitHistory.size() - 1);
            removedExhibit.visited = -1; //reset exhibit visited and update dao
            dao.update(removedExhibit);
            targetExhibit = removedExhibit;
            //TODO somehow suppress replan for back button click?
            updateAllText();
        }
    }

    /**
     * Event handler for clicking the skip button.
     * @param view The View which contains the button.
     */
    public void onSkipButtonClicked(View view) {
        //TODO unselect this exhibit before replanning by calling viewmodel toggleselected
        if(dao.getUnvisited().size() == 0) { //no more exhibits to visit
            if (targetExhibit.identity.equals("entrance_exit_gate")) { //already going to exit
                Utilities.showAlert(this, "You've reached the end of the plan.");
                return;
            } else { //no more exhibits but not navigating to exit, go to exit
                targetExhibit = dao.get("entrance_exit_gate");
            }
        }
        else if(dao.getUnvisited().size() == 1) {
            viewModel.toggleSelected(targetExhibit); //skipping the last exhibit
            targetExhibit = dao.get("entrance_exit_gate"); //go to exit
        }
        else {
            viewModel.toggleSelected(targetExhibit);
            replan();
        }
        updateAllText();
    }

    /**
     * Event handler for clicking the next button.
     * @param view The View which contains the button.
     */
    public void onNextButtonClicked(View view) {
        if(dao.getUnvisited().size() == 0) { //no more exhibits to visit
            if (targetExhibit.identity.equals("entrance_exit_gate")) { //already going to exit
                Utilities.showAlert(this, "You've reached the end of the plan.");
                return;
            }
            else { //no more exhibits but not navigating to exit, go to exit
                targetExhibit = dao.get("entrance_exit_gate");
            }
        }
        else { //still more exhibits to visit
            visitHistory.add(targetExhibit); //add last exhibit to visit history, update dao
            targetExhibit.visited = visitHistory.size(); //visited starts at 1
            dao.update(targetExhibit);
            targetExhibit = Directions.getClosestUnvisitedExhibit(targetExhibit);
            if(targetExhibit == null) {
                targetExhibit = dao.get("entrance_exit_gate");
            }
        }

        updateAllText();
        replanPrompted = false;
    }

    /**
     * Event handler for toggling the detailed directions switch.
     * @param view The View which contains the switch.
     */
    public void onDirectionsSwitchToggled(View view) {
        detailedDirections = detailedSwitch.isChecked();
        updateDirections();
    }

    /**
     * Event handler for toggling the mock location switch.
     * @param view The View which contains the switch.
     */
    public void onMockButtonClicked(View view) {
        useMockLocation = mockSwitch.isChecked();
    }

    /**
     * Utility method. Updates large name text, directions, and next exhibit text.
     */
    private void updateAllText() {
        exhibitName.setText(targetExhibit.name); //set large name text

        Exhibit nextExhibit;
        if(dao.getUnvisited().size() != 0) { //not navigating to exit gate
            if (dao.getUnvisited().size() == 1) { //currently on last exhibit
                nextExhibit = dao.get("entrance_exit_gate"); //manually set next to exit gate
            } else { //not currently on last exhibit, automatically find next
                nextExhibit = Directions.getNextUnvisitedExhibit(targetExhibit);
            }
            List<IdentifiedWeightedEdge> nextPath = Directions.findShortestPath(targetExhibit, nextExhibit);
            int pathLength = Directions.calculatePathWeight(nextPath);

            String nextExhibitText = "Next: " + nextExhibit.name + ", " + pathLength + " ft";
            nextText.setText(nextExhibitText); //set next exhibit text
        }
        else { //navigating to exit gate, no next exhibit
            nextText.setText("");
        }

        updateDirections();
    }

    /**
     * Utility method. Updates directions text.
     */
    public void updateDirections() {
        if(detailedDirections) {
            directionsText.setText(getDetailedDirections());
        }
        else {
            directionsText.setText((getBriefDirections()));
        }
    }

    /**
     * Utility method. Calculates detailed directions to the next exhibit.
     * @return A String representing the detailed directions to the next exhibit.
     */
    private String getDetailedDirections() {
        List<IdentifiedWeightedEdge> path = Directions.findShortestPath(userCurrentExhibit, targetExhibit);

        String directions = ""; // default empty string
        String lastStreetName = "IMPOSSIBLE STREET NAME";
        Exhibit currentNode = userCurrentExhibit;
        Exhibit nextNode = targetExhibit; //default value

        for(int edgeNum = 0; edgeNum < path.size(); edgeNum++) { //iterate through edges in path
            IdentifiedWeightedEdge currEdge = path.get(edgeNum); //single edge
            nextNode = getNextNode(currEdge, currentNode); //next vertex
            String streetName = Directions.getEdgeInfo().get(currEdge.getId()).street; //name of edge
            int distance = (int) Directions.getGraph().getEdgeWeight(currEdge); //edge "length"

            if(edgeNum > 0) { //add newline if not first direction
                directions = directions + "\n";
            }

            //add "Proceed on" / "Continue on"
            if(!streetName.equals(lastStreetName)) { //new street encountered
                directions = directions + "Proceed on "; //new street convention
                lastStreetName = streetName; //update last street encountered
            }
            else { //on the same street as last edge
                directions = directions + "Continue on "; //no need to update last street name
            }

            directions = directions + streetName + " " + distance + " ft "; //add street+distance

            if(edgeNum == path.size() - 1) { //add "towards" / "to" for last exhibit
                directions = directions + "to ";
            }
            else { //not last exhibit
                directions = directions + "towards ";
            }

            directions = directions + nextNode.name; //add next node name
            currentNode = nextNode;
        }
        if(nextNode.isExhibitGroup()) { //add directions for finding exhibit inside group
            directions = directions + " and find " + targetExhibit.name + " inside";
        }
        int totalDistance = Directions.calculatePathWeight(path);
        return directions + "\n\nArriving in " + totalDistance + " ft"; //add distance to exhibit
    }

    /**
     * Utility method. Calculates brief directions to the next exhibit.
     * @return A String representing the brief directions to the next exhibit.
     */
    private String getBriefDirections() {
        List<IdentifiedWeightedEdge> path = Directions.findShortestPath(userCurrentExhibit, targetExhibit);

        String directions = ""; // default empty string
        String lastStreetName = "IMPOSSIBLE STREET NAME";

        for(int edgeNum = 0; edgeNum < path.size(); edgeNum++) { //iterate through edges in path
            IdentifiedWeightedEdge currEdge = path.get(edgeNum); //single edge
            String streetName = Directions.getEdgeInfo().get(currEdge.getId()).street; //name of edge

            if(edgeNum <= 0) { //Add "Proceed down" / "Then down" for first exhibit
                directions = "Proceed down " + streetName;
                lastStreetName = streetName;
            }
            else if(!streetName.equals(lastStreetName)) { //new street encountered
                directions = directions + "\nThen down " + streetName; //new street convention
                lastStreetName = streetName; //update last street encountered
            }
        }
        if(!targetExhibit.equals(Directions.getParent(targetExhibit))) { //has parent - grouped
            String parentName = Directions.getParent(targetExhibit).name;
            directions = directions + "\nFind " + targetExhibit.name + " inside " + parentName;
        }
        int totalDistance = Directions.calculatePathWeight(path);
        return directions + "\n\nArriving in " + totalDistance + " ft"; //add distance to exhibit
    }

    /**
     * Utility method that returns the node at the end of the edge away from a given node.
     * @param edge The edge to be analyzed.
     * @param node The source node.
     * @return Whichever of the edge's source/target is NOT the input node.
     */
    private Exhibit getNextNode(IdentifiedWeightedEdge edge, Exhibit node) {
        String edgeSource = Directions.getGraph().getEdgeSource(edge); //potential other node
        if(node.identity.equals(edgeSource)) { //node is the same as potential node, return other end of edge
            return dao.get(Directions.getGraph().getEdgeTarget(edge));
        }
        return dao.get(edgeSource); //node not same as potential node, return this end of edge
    }

    /**
     * Handles changes in the user's coordinates.
     * @param location The Location object representing the user's current location.
     */
    public void locationChangedHandler(Location location) {
        double lat;
        double lng;

        if (useMockLocation) {
            EditText lat_view = this.findViewById(R.id.mock_lat);
            EditText lon_view = this.findViewById(R.id.mock_lon);

            /*
             * When "use mock" toggle is on, lat/lng fields will be parsed. If lat/lng
             * fields are ever non-double data types (or null), location will be "mocked
             * to entrance_exit_gate.
             */
            try {
                lat = Double.parseDouble(lat_view.getText().toString());
                lng = Double.parseDouble(lon_view.getText().toString());
            } catch (Exception e) { //if coordinates are not valid, default to entrance
                lat = 32.73459618734685;
                lng = -117.14936;
            }
        } else { //not using mock, use actual location
            lat = location.getLatitude();
            lng = location.getLongitude();
        }

        Exhibit closestExhibit = Directions.getClosestAbsoluteExhibit(lat, lng);
        if(closestExhibit != null && !closestExhibit.equals(userCurrentExhibit)) { //closest exhibit != current exhibit
            updateCurrentExhibit(closestExhibit); //update user current exhibit
        }
    }


    /**
     * Utility method. Handles changes in the user's current exhibit (the one they are closest to).
     * @param exhibit The new closest Exhibit to the user.
     */
    private void updateCurrentExhibit(Exhibit exhibit) {
        userCurrentExhibit = exhibit; //update instance variable

        //off track detection
        Exhibit closestUnvisitedExhibit = Directions.getClosestUnvisitedExhibit(userCurrentExhibit);
        //System.out.println(closestUnvisitedExhibit);
        //System.out.println(targetExhibit);
        if(closestUnvisitedExhibit != null && !targetExhibit.equals(closestUnvisitedExhibit)) {
            //user is off track - closer to another unvisited exhibit
            if(!replanPrompted) { //user has not yet been prompted for a replan
                promptReplan();
                replanPrompted = true; //don't replan a second time
            }
        }

        updateDirections(); //update directions to next exhibit
    }

    /**
     * Displays a message to replan the exhibit with yes and no choices.
     */
    public void promptReplan() {
        alertDialog.show();
    }

    /**
     * Utility method. Handles a replan of the park route.
     */
    public void replan() {
        targetExhibit = Directions.getClosestUnvisitedExhibit(userCurrentExhibit);
        updateAllText();
    }
}