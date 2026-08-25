# GijonSmartParking — Backend REST (Flask)

API REST propria per gestione utenti (login multi-utente), parcheggi preferiti e
posizione dell'auto salvata. Copre i requisiti "9 — REST API su server remoto
proprio" e "10 — Storage via REST" del progetto.

Stack: **Flask + Flask-SQLAlchemy + SQLite** (promuovibile a Postgres in
produzione), autenticazione **JWT Bearer token** (`PyJWT`), password con
`werkzeug.security` (hash, mai in chiaro).

## Struttura

```
backend/
├── app.py            # rotte REST + decoratore token_required
├── models.py         # modelli SQLAlchemy: User, Favorite, CarLocation
├── requirements.txt
├── Dockerfile
└── uploads/          # foto del posto auto (multipart/form-data)
```

## Setup locale

```bash
cd backend
python3 -m venv venv
source venv/bin/activate
pip install -r requirements.txt
python app.py
```

Il server parte su `http://localhost:5000`. Il DB SQLite (`parking.db`) viene
creato automaticamente al primo avvio.

## Endpoint

Tutte le rotte protette richiedono l'header:

```
Authorization: Bearer <token>
```

### Registrazione

```bash
curl -X POST http://localhost:5000/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email": "test@example.com", "password": "secret123"}'
```

Risposta `201`:
```json
{"id": 1, "email": "test@example.com"}
```

### Login

```bash
curl -X POST http://localhost:5000/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email": "test@example.com", "password": "secret123"}'
```

Risposta `200`:
```json
{"token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."}
```

Da qui in avanti si assume:
```bash
TOKEN="eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
```

### Login/registrazione con Google

Registrazione rapida: l'app Android ottiene un ID token da Google (Credential
Manager) e lo manda qui; se l'email non esiste ancora viene creato un nuovo
utente (senza password, quella non serve più), altrimenti si fa login
sull'utente esistente. In entrambi i casi la risposta include lo stesso JWT
che restituisce `/auth/login`, più l'email (che il client non ha altrimenti
modo di conoscere da un ID token grezzo).

```bash
curl -X POST http://localhost:5000/auth/google \
  -H "Content-Type: application/json" \
  -d '{"id_token": "<id token restituito da Google sull'\''app>"}'
```

Risposta `200`:
```json
{"token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...", "email": "utente@esempio.com"}
```

Richiede la variabile d'ambiente `GOOGLE_WEB_CLIENT_ID` (vedi tabella di
configurazione più sotto) — senza, ogni token viene rifiutato con `401`.

### Preferiti

**Lista preferiti**
```bash
curl http://localhost:5000/favorites \
  -H "Authorization: Bearer $TOKEN"
```

**Aggiungi preferito**
```bash
curl -X POST http://localhost:5000/favorites \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"parking_id": 123456789}'
```

**Rimuovi preferito**
```bash
curl -X DELETE http://localhost:5000/favorites/123456789 \
  -H "Authorization: Bearer $TOKEN"
```

### Posizione auto

**Leggi ultima posizione salvata**
```bash
curl http://localhost:5000/car-location \
  -H "Authorization: Bearer $TOKEN"
```

**Salva posizione (JSON, senza foto)**
```bash
curl -X POST http://localhost:5000/car-location \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"lat": 43.5357, "lng": -5.6615}'
```

**Salva posizione con foto (multipart/form-data)**
```bash
curl -X POST http://localhost:5000/car-location \
  -H "Authorization: Bearer $TOKEN" \
  -F "lat=43.5357" \
  -F "lng=-5.6615" \
  -F "photo=@/percorso/locale/foto.jpg"
```

La foto viene salvata in `uploads/` e servita da `GET /uploads/<filename>`;
la risposta include `photo_url` con il path relativo da concatenare al
`BASE_URL` del backend.

**Cancella posizione salvata**
```bash
curl -X DELETE http://localhost:5000/car-location \
  -H "Authorization: Bearer $TOKEN"
```

### Account

**Leggi profilo**
```bash
curl http://localhost:5000/account \
  -H "Authorization: Bearer $TOKEN"
```

**Aggiorna profilo (JSON, senza foto)**
```bash
curl -X PUT http://localhost:5000/account \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"first_name": "Mario", "last_name": "Rossi", "home_address": "Via Roma 1, Gijón"}'
```

**Aggiorna profilo con foto (multipart/form-data)**
```bash
curl -X PUT http://localhost:5000/account \
  -H "Authorization: Bearer $TOKEN" \
  -F "first_name=Mario" \
  -F "last_name=Rossi" \
  -F "home_address=Via Roma 1, Gijón" \
  -F "photo=@/percorso/locale/foto.jpg"
```

**Elimina account** (cancella anche preferiti e posizione auto, in cascata)
```bash
curl -X DELETE http://localhost:5000/account \
  -H "Authorization: Bearer $TOKEN"
```

### Health check

```bash
curl http://localhost:5000/health
```

## Deploy

### Opzione A — PythonAnywhere (consigliata)

1. Creare un account free su [pythonanywhere.com](https://www.pythonanywhere.com).
2. Caricare i file di `backend/` (via Git o upload manuale) nella home
   dell'utente PythonAnywhere.
3. In una Bash console PythonAnywhere:
   ```bash
   cd backend
   pip install --user -r requirements.txt
   ```
4. Nella scheda **Web**, creare una nuova web app "Manual configuration"
   (Python 3.11), impostando il file WSGI generato per importare `app`
   da `app.py`:
   ```python
   import sys
   path = "/home/<tuo-utente>/backend"
   if path not in sys.path:
       sys.path.insert(0, path)
   from app import app as application
   ```
5. Il DB SQLite (`parking.db`) e la cartella `uploads/` restano persistenti
   sul filesystem di PythonAnywhere tra un reload e l'altro.
6. L'URL pubblico sarà del tipo `https://<tuo-utente>.pythonanywhere.com`.

### Opzione B — Docker

```bash
cd backend
docker buildx build -t gijonparking-api .
docker run -d -p 5000:5000 -e SECRET_KEY="cambia-questa-chiave" gijonparking-api
```

> Nota: con Docker il file `parking.db` e `uploads/` vivono dentro il
> container e vengono persi alla rimozione del container, a meno di montare
> un volume (`-v $(pwd)/data:/app/data`, adattando `SQLALCHEMY_DATABASE_URI`).

## Configurazione

| Variabile              | Default                | Descrizione                          |
|------------------------|--------------------------|---------------------------------------|
| `SECRET_KEY`           | `dev-secret-change-me`   | Chiave di firma dei JWT — **da cambiare in produzione** |
| `GOOGLE_WEB_CLIENT_ID` | (vuoto)                  | Web Client ID del progetto Firebase, usato per verificare i token di `/auth/google`. Si trova in Firebase Console → Project settings → il "Web client ID" creato automaticamente abilitando Google come provider di Authentication (oppure nel `google-services.json` scaricato dopo averlo abilitato, campo `oauth_client` con `client_type: 3`). |

## Collegamento con l'app Android

Nel progetto Android, impostare `BASE_URL` del `BackendApiService` (in
`NetworkModule.kt`, Task 2) sull'URL pubblico di questo backend, es.
`https://<tuo-utente>.pythonanywhere.com/`.
