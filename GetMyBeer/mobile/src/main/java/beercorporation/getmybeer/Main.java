package beercorporation.getmybeer;

import android.app.AlertDialog;
import android.app.SearchManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.SearchView;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;


public class Main extends ActionBarActivity implements SearchView.OnQueryTextListener {

    private String protocol = "http";
    private String host = "binouze.fabrigli.fr";
    private int[] beersID; //Tableau contenant les IDs de chaque bière
    private ArrayList<HashMap<String, Object>> beersDetails; //Liste contenant les détails complémentaires de chaque bière
    private ProgressBar progressBar;
    private ListView myBeerListView; //ListView permettant d'afficher tous les bières

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        progressBar = (ProgressBar) findViewById(R.id.progressBar);
        myBeerListView = (ListView) findViewById(R.id.beerListView);

        //Appel d'un AsyncTask permettant de pouvoir récupérer tous les IDs de chaque bière
        new DLTask().execute(protocol + "://" + host + "/bieres.json");
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);

        // Associate searchable configuration with the SearchView
        SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
        SearchView searchView = (SearchView) menu.findItem(R.id.action_search).getActionView();
        searchView.setSearchableInfo(searchManager.getSearchableInfo(getComponentName()));
        searchView.setSubmitButtonEnabled(true);
        searchView.setOnQueryTextListener(this);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_search) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    /*Fonction appelé lorque l'on recherche le nom d'une bière*/
    @Override
    public boolean onQueryTextSubmit(String query) {
        for(int i = 0; i < beersDetails.size(); i++) {
            //Ici il suffit juste de chercher si "query" est contenu dans le nom d'une bière
            if ((((String) beersDetails.get(i).get("name")).toLowerCase()).contains(query.toLowerCase())) {
                myBeerListView.smoothScrollToPositionFromTop(i, 0);
                return true;
            }
        }
        return false;
    }

    /*Fonction appelé lorque l'on tape le nom d'une bière*/
    @Override
    public boolean onQueryTextChange(String newText) {
        for(int i = 0; i < beersDetails.size(); i++) {
            //Ici il suffit juste de chercher si "query" commence par le nom d'une bière ou s'il y est contenu
            if ((((String) beersDetails.get(i).get("name")).toLowerCase()).startsWith(newText.toLowerCase()) ||
                    (((String) beersDetails.get(i).get("name")).toLowerCase()).contains(newText.toLowerCase())) {
                myBeerListView.smoothScrollToPositionFromTop(i, 0);
                return true;
            }
        }
        return false;
    }

    /*Fonction permettant de savoir si le téléphone est connecté à Internet ou pas*/
    private boolean isOnline() {
        ConnectivityManager myCM = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo myNetInfo = myCM.getActiveNetworkInfo();
        return myNetInfo != null && myNetInfo.isConnected();
    }

    /*AsyncTask permettant de télécharger tous les IDs de chaque bière*/
    private class DLTask extends AsyncTask<String, Void, String> {
        @Override
        protected String doInBackground(String... urls) {
            if (isOnline()) {
                try {
                    return downloadWebContent(urls[0]);
                } catch (IOException e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(getApplicationContext(), "Erreur URL : URL invalide. Impossible de retrouver la page web.", Toast.LENGTH_LONG).show();
                        }
                    });
                    return "";
                }
            } else {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(getApplicationContext(), "Erreur de connexion : Connexion au serveur impossible. Veuillez vérifier votre connexion Internet et réessayer.", Toast.LENGTH_LONG).show();
                    }
                });
                return "";
            }
        }

        @Override
        protected void onPostExecute(String webContent) {
            progressBar.setVisibility(View.VISIBLE);
            if(!webContent.isEmpty()) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(getApplicationContext(), "Téléchargement terminé !\nTraitement en cours...", Toast.LENGTH_LONG).show();
                    }
                });
                beersID = getBeersID(parseBeersDetails(webContent)); //beersID contient maintenant tous les IDs
                beersDetails = new ArrayList<HashMap<String, Object>>(); //Initialisation de la liste
                for (int i = 0; i < beersID.length; i++) {
                    //Appel d'un deuxième AsyncTask permettant de récupérer tous les détails complémentaires de chaque bière à partir de leur ID
                    new DLTask2().execute(protocol + "://" + host + "/bieres/" + beersID[i] + ".json");
                    progressBar.setProgress(((i+1) * 100)/beersID.length);
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(getApplicationContext(), "Traitement terminé !\nConstruction de l'affichage en cours...", Toast.LENGTH_LONG).show();
                    }
                });
            }
            progressBar.setVisibility(View.GONE);
        }

        /*Fonction permettant de télécharger le contenu de la page donnée en paramètre*/
        private String downloadWebContent(String url) throws IOException {
            BufferedReader in = new BufferedReader(new InputStreamReader(new URL(url).openStream()));
            String webContent = readStream(in);
            in.close();
            return webContent;
        }

        /*Fonction permettant de lire le contenu de la page téléchargée*/
        private String readStream(BufferedReader in) throws IOException {
            String lines = "";
            String inputLine;
            while ((inputLine = in.readLine()) != null)
                lines += inputLine;
            return lines;
        }

        /*Fonction permettant de découper le contenu de la page*/
        private String[] parseBeersDetails(String webContent) {
            //On retire le caractère "[" et "]" de webContent avant de le parser
            String webContentWithoutBrackets = webContent.substring(1, webContent.length() - 1);
            //On parse à chaque fois que l'on voit exactement cette suite de caractères "},{"
            String[] beersDetails = webContentWithoutBrackets.split("\\},\\{"); //Ainsi à chaque indice on a les détails d'une seule bière
            /*Uniformisation du contenu du tableau "beersDetails"*/
            //A l'indice 0 on retire le caractère "{" du string
            beersDetails[0] = beersDetails[0].substring(1);
            //A la dernière indice on retire "}" du string
            beersDetails[beersDetails.length - 1] = beersDetails[beersDetails.length - 1].substring(0, beersDetails[beersDetails.length - 1].length() - 1);
            return beersDetails;
        }

        /*Fonction permettant de récupérer les IDs de chaque bière*/
        private int[] getBeersID(String[] beersDetails) {
            int[] beersID = new int[beersDetails.length];
            for (int i = 0; i < beersDetails.length; i++) {
                String[] details = beersDetails[i].split(",");
                for (String detail : details) {
                    if (detail.contains("\"id\":")) {
                        try {
                            beersID[i] = Integer.parseInt(detail.split(":")[1]);
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
            }
            return beersID;
        }
    }

    /*AsyncTask permettant de télécharger tous les détails de chaque bière*/
    private class DLTask2 extends DLTask {
        @Override
        protected void onPostExecute(String webContent) {
            if(!webContent.isEmpty()) {
                beersDetails.add(getUsefulBeerDetails(webContent));
                if(beersID.length == beersDetails.size()) {//Lorsque l'on a récupéré tous les infos utiles de tous les bières

                    /*On crée un Thread à part pour trier les données (Opération longue)*/
                    Thread threadForSort = new Thread(new Runnable() {
                        @Override
                        public void run() {
                            sortDetailsByBeerName(); //Appel de la fonction de trie
                        }
                    });
                    threadForSort.start(); //On lance le Thread crée
                    try {
                        threadForSort.join();//On attend que le Thread est terminé pour continuer
                    } catch (final InterruptedException e) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(getApplicationContext(), e.getMessage(), Toast.LENGTH_LONG).show();
                            }
                        });
                    }

                    /*On télécharge aussi les images de chaque bière*/
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(getApplicationContext(), "Patientez encore quelques instants...", Toast.LENGTH_LONG).show();
                        }
                    });
                    int i;
                    for(i = 0; i < beersDetails.size(); i++){
                        //Appel d'un AsyncTask permettant de récupérer les images de chaque bière
                        new DLImageTask(i).execute((String) beersDetails.get(i).get("imgURL"));
                    }

                }
            }
        }

        /*Fonction permettant de récupérer que les infos utiles à afficher*/
        private HashMap<String, Object> getUsefulBeerDetails(String webContent) {
            HashMap<String, Object> usefulBeerDetails = new HashMap<String, Object>();
            //On retire le caractère "{" et "}" de webContent avant de le parser
            String webContentWithoutBrackets = webContent.substring(1, webContent.length() - 1);

            //On parse à chaque fois que l'on voit exactement le caractère ":" ou ","
            String[] tokens = webContentWithoutBrackets.split(":|,");

            for(int i = 0; i < tokens.length; i++){
                if(tokens[i].equals("\"id\"")){
                    usefulBeerDetails.put("id", tokens[i+1]);
                }
                else if(tokens[i].equals("\"category\"")){
                    usefulBeerDetails.put("category", tokens[i+1].substring(1, tokens[i+1].length() - 1));
                }
                else if(tokens[i].equals("\"description\"")){
                    usefulBeerDetails.put("description", tokens[i+1].substring(1, tokens[i+1].length() - 1));
                }
                else if(tokens[i].equals("\"thumb\"")){
                    usefulBeerDetails.put("imgURL", protocol + "://" + host + tokens[i+2].substring(1, tokens[i+2].length() - 4));
                }
                else if(tokens[i].equals("\"name\"") && !usefulBeerDetails.containsKey("name")){//Ici on fait une vérification en plus pour ne récupérer que le 'name' de la bière
                    usefulBeerDetails.put("name", tokens[i+1].substring(1, tokens[i+1].length() - 1));
                }
                else if(tokens[i].equals("\"country\"")){
                    usefulBeerDetails.put("country", tokens[i+6].substring(1, tokens[i+6].length() - 2));
                }
                else if(tokens[i].equals("\"note_moyenne\"")){
                    usefulBeerDetails.put("note_moyenne", tokens[i+1]);
                }
                else if(tokens[i].equals("\"number_of_notes\"")){
                    usefulBeerDetails.put("number_of_notes", tokens[i+1]);
                }
            }
            return usefulBeerDetails;
        }

        /*Fonction permettant de trier les détails par rapport au nom de chaque bière*/
        private void sortDetailsByBeerName(){
            Collections.sort(beersDetails, new Comparator<HashMap<String, Object>>() {
                @Override
                public int compare(HashMap<String, Object> lhs, HashMap<String, Object> rhs) {
                    return ((String) lhs.get("name")).compareTo((String) rhs.get("name"));
                }
            });
        }
    }

    /*AsyncTask permettant de télécharger l'image d'une bière*/
    private class DLImageTask extends AsyncTask<String, Void, Bitmap>{
        private int position;

        public DLImageTask(int position){
            super();
            this.position = position; //On récupère la position du HashMap dans la liste pour pouvoir mettre l'image téléchargée dans le HashMap
        }

        @Override
        protected Bitmap doInBackground(String... urls) {
            if (isOnline()) {
                try {
                    return downloadImage(urls[0]);
                } catch (IOException e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(getApplicationContext(), "Erreur URL : URL invalide. Impossible de retrouver l'image.", Toast.LENGTH_LONG).show();
                        }
                    });
                    return null;
                }
            } else {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(getApplicationContext(), "Erreur de connexion : Connexion au serveur impossible. Veuillez vérifier votre connexion Internet et réessayer.", Toast.LENGTH_LONG).show();
                    }
                });
                return null;
            }
        }

        @Override
        protected void onPostExecute(Bitmap downloadedImage) {
            if(downloadedImage != null){
                beersDetails.get(position).put("img", downloadedImage); //On met dans le HashMap l'image téléchargée
                if(position == beersDetails.size() - 1){ //Lorsque l'on a récupéré tous les images de tous les bières
                    //On affiche maintenant la ListView vue que l'on a tous les informations de chaque bière
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            showBeerList();
                        }
                    });
                }
            }
        }

        /*Fonction permettant de télécharger l'image grâce à l'URL passé en paramètre*/
        private Bitmap downloadImage(String url) throws IOException {
            InputStream in = new URL(url).openStream();
            Bitmap downloadedImg = BitmapFactory.decodeStream(in);
            in.close();
            return downloadedImg;
        }

        /*Fonction permettant d'afficher la ListView*/
        private void showBeerList() {
            SimpleAdapter myAdapter = new SimpleAdapter(
                    getBaseContext(),
                    beersDetails,
                    R.layout.beer_item_row,
                    new String[]{"img", "name", "description"},
                    new int[]{R.id.beerImg, R.id.beerName, R.id.beerDescription}
            );
            myAdapter.setViewBinder(new MyViewBinder());
            myBeerListView.setAdapter(myAdapter);

            myBeerListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView a, View v, int position, long id) {
                    HashMap<String, Object> myMap = (HashMap<String, Object>) myBeerListView.getItemAtPosition(position);

                    //Lorsque l'on clique sur un item de la ListView
                    //On affiche les détails complémentaire de la bière
                    AlertDialog.Builder myAdb = new AlertDialog.Builder(Main.this);
                    myAdb.setTitle("Détail de votre choix");

                    LayoutInflater factory = LayoutInflater.from(Main.this);
                    final View view = factory.inflate(R.layout.detail_dialog, null);

                    TextView beerName = (TextView) view.findViewById(R.id.beerName2);
                    beerName.setText((String) myMap.get("name"));

                    ImageView beerImg = (ImageView) view.findViewById(R.id.beerImg2);
                    beerImg.setImageBitmap((Bitmap) myMap.get("img"));

                    TextView beerOtherDetails = (TextView) view.findViewById(R.id.beerOtherDetails);
                    beerOtherDetails.setText(
                            "Description : \n" + myMap.get("description") + "\n\n" +
                            "Catégorie : \n" + myMap.get("category") + "\n\n" +
                            "Pays : " + myMap.get("country") + "\n\n" +
                            "Note moyenne : " + myMap.get("note_moyenne") + "\n\n" +
                            "Nombre de notes : " + myMap.get("number_of_notes")
                    );

                    myAdb.setView(view);

                    myAdb.setPositiveButton("OK", null);
                    myAdb.show();
                }
            });
        }
    }

    private class MyViewBinder implements SimpleAdapter.ViewBinder {
        @Override
        public boolean setViewValue(final View view, final Object data, String textRepresentation) {
            if(view instanceof ImageView && data instanceof Bitmap) {
                ((ImageView) view).setImageBitmap((Bitmap) data);
                return true;
            }
            if(view instanceof TextView && data instanceof String) {
                ((TextView) view).setText((String) data);
                return true;
            }
            return false;
        }
    }
}
