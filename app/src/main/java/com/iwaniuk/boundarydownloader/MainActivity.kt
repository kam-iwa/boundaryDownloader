package com.iwaniuk.boundarydownloader

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Environment
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import org.osmdroid.api.IMapController
import org.osmdroid.bonuspack.kml.KmlDocument
import org.osmdroid.config.Configuration
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.FolderOverlay
import java.io.File


class MainActivity : AppCompatActivity() {
    private val STORAGE_PERMISSION_CODE = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val myAdView : AdView = findViewById(R.id.ad_banner)
        val adRequest = AdRequest.Builder().build()
        myAdView.loadAd(adRequest)

        //ustawienie user-agent, aby poprawnie wyswietlic OSM
        val myContext: Context = applicationContext
        Configuration.getInstance().userAgentValue = BuildConfig.APPLICATION_ID

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED){
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)){
                AlertDialog.Builder(this)
                    .setTitle(getString(R.string.app_need_perm_title))
                    .setMessage(getString(R.string.app_need_perm_desc))
                    .setPositiveButton(getString(R.string.app_need_perm_positive), DialogInterface.OnClickListener() { dialogInterface: DialogInterface, i: Int ->
                        ActivityCompat.requestPermissions(this@MainActivity, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), STORAGE_PERMISSION_CODE)
                    })
                    .setNegativeButton(getString(R.string.app_need_perm_negative), DialogInterface.OnClickListener(){ dialogInterface: DialogInterface, i: Int ->
                        dialogInterface.dismiss()
                    })
                    .create().show()
            } else {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), STORAGE_PERMISSION_CODE)
            }
        }

        val myKml = KmlDocument()
        val myMapView : MapView = findViewById(R.id.map_view)

        //widok mapy
        myMapView.isClickable = true
        myMapView.setMultiTouchControls(true)
        myMapView.setUseDataConnection(true)
        myMapView.minZoomLevel = 4.0

        val myMapController : IMapController = myMapView.controller
        myMapController.setZoom(4.0)

        //pobieranie_granic
        val myDownloadButton : Button = findViewById(R.id.download_border)
        myDownloadButton.setOnClickListener {
            val myInput : TextView = findViewById(R.id.input)
            downloadBorder(myMapView, myKml, myContext, myInput)
        }

        val mySaveButton : Button = findViewById(R.id.save_border)
        mySaveButton.setOnClickListener {
            val myInput : TextView = findViewById(R.id.input)
            saveBorder(myMapView, myKml, myContext, myInput)
            }
        }

    fun downloadBorder(myMapView : MapView, myKml : KmlDocument, myContext: Context, myInput: TextView){
        //czyszczenie mapy
        if(myMapView.overlays.size > 0){
            myMapView.overlays.removeAt(myMapView.overlays.lastIndex)
            myMapView.invalidate()
        }

        //odczyt nazwy obszaru z pola tekstowego
        val borderName = myInput.text.toString()

        //html request
        val myQueue = Volley.newRequestQueue(myContext)
        val myUrl = "https://nominatim.openstreetmap.org/search?q=$borderName&format=geojson&limit=1&polygon_geojson=1"
        val myRequest = JsonObjectRequest(
            Request.Method.GET, myUrl, null,
            Response.Listener { response ->
                if(response.getJSONArray("features").length() == 0){
                    Toast.makeText(myContext, getString(R.string.app_features_length_0), Toast.LENGTH_SHORT).show()
                } else {
                    if(response.getJSONArray("features").getJSONObject(0).getJSONObject("geometry").getString("type") == "Polygon" ||
                        response.getJSONArray("features").getJSONObject(0).getJSONObject("geometry").getString("type") == "MultiPolygon"){

                        myKml.parseGeoJSON(response.toString())
                        val myOverlay = myKml.mKmlRoot.buildOverlay(myMapView, null, null, myKml) as FolderOverlay
                        myMapView.overlays.add(myOverlay)
                        val myBox = myKml.mKmlRoot.boundingBox
                        myMapView.zoomToBoundingBox(myBox, true)
                        myMapView.controller.setCenter(myBox.centerWithDateLine)
                        myMapView.invalidate()

                    } else {
                        Toast.makeText(myContext, getString(R.string.app_features_is_not_polygon), Toast.LENGTH_SHORT).show()
                    }
                }
            },
            Response.ErrorListener { error ->
                Toast.makeText(myContext, getString(R.string.app_features_download_error), Toast.LENGTH_SHORT).show()
            }
        )
        myQueue.add(myRequest)
    }

    fun saveBorder(myMapView : MapView, myKml : KmlDocument, myContext: Context, myInput: TextView) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED){
            try{
                val myDirectory = File(Environment.getExternalStorageDirectory().toString() + "/kml/borders")
                if (!myDirectory.exists()) {
                    myDirectory.mkdirs()
                }
                val borderName = myInput.text.toString()
                val localFile: File = myKml.getDefaultPathForAndroid("/borders/$borderName.geojson")
                if (myMapView.overlays.size > 0) {
                    myKml.saveAsGeoJSON(localFile)
                    Toast.makeText(myContext, getString(R.string.app_saving_succesful) + localFile, Toast.LENGTH_SHORT).show()
                    myMapView.overlays.removeAt(myMapView.overlays.lastIndex)
                    myMapView.invalidate()
                } else {
                    Toast.makeText(myContext, getString(R.string.app_saving_empty), Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception){
                Toast.makeText(myContext, getString(R.string.app_saving_error), Toast.LENGTH_SHORT).show()
            }
        }
        else {
            Toast.makeText(myContext, getString(R.string.app_saving_no_perms), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        if(requestCode == STORAGE_PERMISSION_CODE){
            if(grantResults.size > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED){
                Toast.makeText(this, getString(R.string.perm_granted), Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, getString(R.string.perm_denied), Toast.LENGTH_SHORT).show()
            }
        }
    }

}