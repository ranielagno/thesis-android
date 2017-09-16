package raniel.earthquakesearchdrone;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

public class Login extends AppCompatActivity {

    EditText server, password;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        server = (EditText) findViewById(R.id.server);
        password = (EditText) findViewById(R.id.password);
    }

    public void loginServer (View view) {
        BackgroundWorker backgroundWorker = new BackgroundWorker();
        backgroundWorker.execute(server.getText().toString());
    }
}
