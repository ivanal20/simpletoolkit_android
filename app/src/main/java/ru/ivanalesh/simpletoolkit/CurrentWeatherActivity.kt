package ru.ivanalesh.simpletoolkit

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.os.Bundle
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.bumptech.glide.Glide
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class CurrentWeatherActivity : AppCompatActivity() {
    private val retrofit = Retrofit.Builder()
        .baseUrl("https://api.openweathermap.org/data/2.5/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
    private val weatherApi = retrofit.create(WeatherApiService::class.java)
    @SuppressLint("NewApi")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_current_weather)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        val backBtn2 = findViewById<ImageButton>(R.id.back_btn_2)
        backBtn2.setOnClickListener { goBack() }


        checkLocationPermission()
    }
    private fun goBack(){
        finish()
    }
    private fun getLastLocation() {
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        val fusedLocationClient : FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)
        fusedLocationClient.lastLocation.addOnSuccessListener {
            location : Location? ->
            if (location != null) {
                val latitude = location.latitude
                val longitude = location.longitude
                getCityName(latitude, longitude)
                getWeather(latitude, longitude)
            }
        }
    }
    fun formatUnixTime(unixTime: Long): String {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        sdf.timeZone = TimeZone.getDefault() // Используем локальное время
        return sdf.format(Date(unixTime * 1000)) // Переводим в миллисекунды
    }
    private fun getWeather(lat: Double, lon: Double) {
        val sharedPreferences = getSharedPreferences("WeatherAppPrefs", Context.MODE_PRIVATE)
        val apiKey = sharedPreferences.getString("API_KEY", "") ?: ""
        if (apiKey.isEmpty()){
            Toast.makeText(this, R.string.api_key_not_found_text.toString(), Toast.LENGTH_SHORT).show()
            return
        }

        weatherApi.getWeather(lat, lon, apiKey).enqueue(object : Callback<WeatherResponse> {
            override fun onResponse(
                call: Call<WeatherResponse>,
                response: Response<WeatherResponse>
            ) {
                if (response.isSuccessful){
                    val weatherResponse = response.body()
                    if(weatherResponse != null){
                        val pressureMmHg = (weatherResponse.main.pressure * 0.75006).toInt()
                        val feelsLike = weatherResponse.main.feelsLike.toInt() // Округляем
                        val tempMin = weatherResponse.main.tempMin.toInt()
                        val tempMax = weatherResponse.main.tempMax.toInt()
                        val sunriseTime = formatUnixTime(weatherResponse.sys.sunrise)
                        val sunsetTime = formatUnixTime(weatherResponse.sys.sunset)
                        val tvTemp = findViewById<TextView>(R.id.textViewTemperature)
                        tvTemp.text = "${weatherResponse.main.temp}"
                        val iconUrl = "https://openweathermap.org/img/wn/${weatherResponse.weather[0].icon}@2x.png"
                        val weatherImageView = findViewById<ImageView>(R.id.imageViewWeatherIcon)
                        val tvWeather = findViewById<TextView>(R.id.textViewWeather)
                        Glide.with(this@CurrentWeatherActivity).load(iconUrl).into(weatherImageView)
                        tvWeather.text = """
                            ${R.string.feels_like}: $feelsLike°C
                            ${R.string.min_temp}: $tempMin°C
                            ${R.string.max_temp}: $tempMax°C
                            ${R.string.humidity}: ${weatherResponse.main.humidity}%
                            ${R.string.pressure}: $pressureMmHg мм рт. ст.
                            ${R.string.sunrise}: $sunriseTime
                            ${R.string.sunset}: $sunsetTime
                            ${weatherResponse.weather[0].description.capitalize()}
                        """.trimIndent()
                    }
                }
            }
            override fun onFailure(call: Call<WeatherResponse>, t: Throwable) {
                //val err_text =
                Toast.makeText(this@CurrentWeatherActivity, "${R.string.error_text}: ${t.message}", Toast.LENGTH_LONG).show()
            }
        }
        )
    }

    private fun getCityName(lat: Double, lon: Double){
        val geocoder = Geocoder(this, Locale.getDefault())
        try {
            val addresses = geocoder.getFromLocation(lat, lon, 1)
            if (!addresses.isNullOrEmpty()) {
                val cityName = addresses[0].locality ?: R.string.city_not_found_text.toString()
                val city = findViewById<TextView>(R.id.textViewCity)
                city.text = "$cityName"
            } else {
                val city = findViewById<TextView>(R.id.textViewCity)
                city.text = R.string.city_not_found_text.toString()
            }
        } catch (e : Exception){
            val city = findViewById<TextView>(R.id.textViewCity)
            city.text = R.string.city_error_text.toString()
            e.printStackTrace()
        }
    }

    private fun textView(): TextView? {
        val city = findViewById<TextView>(R.id.textViewCity)
        return city
    }

    private fun checkLocationPermission(){
        val requestPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ){
            isGranted: Boolean ->
            if (isGranted)
            {
                getLastLocation()
            } else {
                Toast.makeText(this, R.string.gps_perm_not_granted.toString(), Toast.LENGTH_SHORT).show()
            }
        }
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED){
            getLastLocation()
        } else {
            requestPermissionLauncher.launch(android.Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    }
/*data class WeatherResponse(
    val weather: List<Weather>,
    val main: Main,
    val wind: Wind,
    val name: String
)

data class Weather(
    val main: String,
    val description: String,
    val icon: String
)

data class Main(
    val temp: Double,
    val feels_like: Double,
    val temp_min: Double,
    val temp_max: Double,
    val pressure: Int,
    val humidity: Int
)

data class Wind(
    val speed: Double,
    val deg: Int
)
interface WeatherApiService {
    @GET("data/2.5/weather")
    suspend fun getWeather(
        @Query("q") city: String,
        @Query("units") units: String = "metric", // Метрическая система (градусы Цельсия)
        @Query("appid") apiKey: String
    ): WeatherResponse
}
object RetrofitClient {
    private const val BASE_URL = "https://api.openweathermap.org/"

    val instance: WeatherApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(WeatherApiService::class.java)
    }
}*/