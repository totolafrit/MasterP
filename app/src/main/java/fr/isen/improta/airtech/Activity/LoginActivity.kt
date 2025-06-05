package fr.isen.improta.airtech.Activity

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.google.firebase.auth.FirebaseAuth
import fr.isen.improta.airtech.LoginScreen

class LoginActivity : ComponentActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = FirebaseAuth.getInstance()

        setContent {
            LoginScreen(
                onLogin = { email, password ->
                    auth.signInWithEmailAndPassword(email, password)
                        .addOnSuccessListener {
                            goToNextStep()
                        }
                        .addOnFailureListener {
                            Toast.makeText(this, "Erreur de connexion", Toast.LENGTH_SHORT).show()
                        }
                },
                onRegister = { email, password ->
                    auth.createUserWithEmailAndPassword(email, password)
                        .addOnSuccessListener {
                            goToNextStep()
                        }
                        .addOnFailureListener {
                            Toast.makeText(this, "Erreur d'inscription", Toast.LENGTH_SHORT).show()
                        }
                }
            )
        }
    }

    private fun goToNextStep() {
        startActivity(Intent(this, ScanActivity::class.java))
        finish()
    }
}
