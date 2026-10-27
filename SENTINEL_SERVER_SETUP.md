# SENTINEL — Local Server Setup and Restart Recovery

This guide explains how to start SENTINEL after a Windows restart and how to let a teacher access the demo from another laptop on the same Wi-Fi/hotspot.

> **Demo safety:** Use only test data and `sentinel_test_db`. Do not run tests against `sentinel_db`, enable router port forwarding, or expose this development server to the public internet. Stop the server when the demo is finished.

## 1. What needs to be running?

SENTINEL runs locally on the host laptop:

- MySQL provides the database.
- The Java 21 process runs the SENTINEL API and serves the web UI on port `8080`.
- The browser opens the host laptop's IP address.
- For another laptop to access it, both devices need to be on a network that permits device-to-device traffic. Some hotspots block this.

If the host laptop shuts down, sleeps, disconnects from the network, or the Java server stops, the other laptop cannot use the live application until the server is started and reachable again.

## 2. Prerequisites

The setup used for this project is:

- Windows
- JDK 21 installed at:
  `C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot`
- MySQL installed and configured locally
- Project folder:
  `D:\Documents\SENTINEL\SENTINEL_UI_EDITION`
- MySQL Connector/J JAR:
  `lib\mysql-connector-j-26.7.0.jar`
- Compiled classes (normally already present):
  `buildcheck_jdk21`

Do not upload database passwords, private credentials, database dumps, or real customer data to GitHub.

## 3. Start MySQL

1. Press `Win + R`.
2. Type `services.msc` and press Enter.
3. Find the installed MySQL service (the name may be `MySQL80` or another name depending on the installation).
4. If it is stopped, select **Start**. If it is already running, leave it running.
5. Close Services.

If MySQL is not installed as a Windows service, start it using the method appropriate to your installation.

## 4. Open a fresh PowerShell window

Use PowerShell for the commands below. These commands are intended to be pasted without the `PS ...>` prompt.

Open PowerShell from the Start menu, then run:

```powershell
cd "D:\Documents\SENTINEL\SENTINEL_UI_EDITION"
```

If this directory does not exist on the computer, first obtain the project files and place them in the expected folder, or update the path in these instructions.

## 5. Configure the test database connection

Environment variables are per terminal session. After a Windows restart or opening a new PowerShell window, set them again.

Run:

```powershell
$env:SENTINEL_DB_URL = "jdbc:mysql://localhost:3306/sentinel_test_db"
$env:SENTINEL_DB_USER = "root"

$securePassword = Read-Host "Enter MySQL password" -AsSecureString
$env:SENTINEL_DB_PASSWORD = [System.Net.NetworkCredential]::new("", $securePassword).Password
Remove-Variable securePassword
```

Enter the local MySQL `root` password when prompted. The password is not written into this guide or the command history.

**Important:** Confirm that the application actually uses these environment variables for its database connection. If the server reports a database connection error, do not switch to `sentinel_db` as a workaround. Check MySQL status, the credentials, and the application's database configuration.

## 6. Find the current laptop IP address

The IP can change when reconnecting to Wi-Fi or a hotspot.

Run:

```powershell
ipconfig
```

Under the connected Wi-Fi adapter, find **IPv4 Address**. For example, the address might be `10.59.36.243`; use the address shown on your own laptop now, not an old example.

Set the exact origin using that IP and port `8080`. Replace the example IP if it differs:

```powershell
$env:SENTINEL_ALLOWED_ORIGIN = "http://10.59.36.243:8080"
```

This matches the `SENTINEL_ALLOWED_ORIGIN` setting in `SentinelApiServer.java`. The server's exact-origin validation should remain enabled. Do not replace it with `*` or disable it.

If you want to use the local-only address instead, the default origin is `http://127.0.0.1:8080`. For a teacher's laptop, use the host's reachable IP and configure the matching origin as shown above.

## 7. Compile (only if needed)

If `buildcheck_jdk21` already contains the compiled classes and you have not changed the Java source, you usually do not need to compile after a normal restart.

If you changed Java source files or the compiled output is missing, run:

```powershell
$jdk21 = "C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot\bin\javac.exe"

New-Item -ItemType Directory -Force "buildcheck_jdk21" | Out-Null

& $jdk21 -cp "lib\mysql-connector-j-26.7.0.jar" -d buildcheck_jdk21 src\*.java
```

If compilation reports errors, stop and fix those errors before starting the server.

## 8. Start the SENTINEL server

From the project folder, with the database and origin environment variables set in the same PowerShell window, run:

```powershell
$java21 = "C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot\bin\java.exe"

& $java21 -cp "buildcheck_jdk21;lib\mysql-connector-j-26.7.0.jar" SentinelApiServer
```

Keep this terminal window open while SENTINEL is in use. A successful start previously reported the API health status as `ok`; exact startup messages may vary.

## 9. Open the web UI

On the host laptop, open this in a browser, replacing the example IP with the current Wi-Fi IPv4 address:

```text
http://10.59.36.243:8080/
```

On the teacher's laptop:

1. Connect both laptops to the same hotspot or Wi-Fi network.
2. Open the same `http://HOST-IP:8080/` address in the teacher's browser.
3. Try the demo login and verify the relevant dashboard actions.

Do not use `localhost` on the teacher's laptop: `localhost` points to the teacher's own computer, not the host laptop.

## 10. If the teacher's laptop cannot connect

Check these in order:

1. Confirm the Java server is still running in the host PowerShell window.
2. Confirm the host's IP address with `ipconfig`; update `SENTINEL_ALLOWED_ORIGIN` and restart the server if the address changed.
3. On the host, check whether the server is listening on all IPv4 interfaces:

   ```powershell
   Get-NetTCPConnection -LocalPort 8080 -State Listen
   ```

   For other devices to connect, the listener should normally show `0.0.0.0` on port `8080`, rather than only `127.0.0.1`.

4. Try the host IP URL in a browser on the host laptop first.
5. Check whether Windows Firewall is blocking incoming connections. Only create a narrowly scoped firewall rule if necessary, and restrict it to the trusted private/demo network. Do not turn the firewall off globally.
6. Some mobile hotspots use client isolation and do not allow connected devices to communicate. If so, use a trusted Wi-Fi network that permits local connections, or run the demo locally on one laptop.

Do not set up router port forwarding or expose port `8080` to the public internet.

## 11. Stop and restart the server

- To stop the server normally, focus its PowerShell window and press `Ctrl + C`.
- If the laptop restarts, start MySQL again if needed, open a new PowerShell window, repeat Sections 4–8, and open the current IP URL.
- If the Wi-Fi IP changed, update the `SENTINEL_ALLOWED_ORIGIN` value before starting the server.
- Closing the terminal or shutting down the host stops the server.

## 12. Common problems

### `javac` or `java` cannot be found

Use the full JDK paths shown in this guide. Confirm JDK 21 is installed in the specified directory.

### Database connection refused

Make sure the MySQL service is running and listening locally. Confirm the database URL, username, password, and database name. Use `sentinel_test_db` for demo testing.

### Login fails after changing the IP

The page origin and `SENTINEL_ALLOWED_ORIGIN` must match exactly, including `http://` and `:8080`. Set the variable before launching Java, then restart the server.

### The host works but the teacher's laptop does not

Check the bind address, current host IP, Windows Firewall, and whether the hotspot blocks communication between connected devices.

### Port 8080 is already in use

Check for an existing SENTINEL Java process or another service using port `8080`. Stop only the process you recognize as the one that should be stopped; do not terminate unknown processes blindly.

---

## Quick restart checklist

- [ ] MySQL service is running.
- [ ] PowerShell is open in the project directory.
- [ ] `SENTINEL_DB_URL` points to `sentinel_test_db`.
- [ ] Database username and password are set for this session.
- [ ] Current Wi-Fi IPv4 address is known.
- [ ] `SENTINEL_ALLOWED_ORIGIN` matches `http://CURRENT-IP:8080`.
- [ ] Java server starts and its terminal remains open.
- [ ] The host can open `http://CURRENT-IP:8080/`.
- [ ] The teacher's laptop is on the same network and can open the URL.
- [ ] Server is stopped after the demo.

