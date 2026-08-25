import os
from datetime import datetime, timedelta, timezone
from functools import wraps

import jwt
from flask import Flask, g, jsonify, request, send_from_directory
from google.auth.transport import requests as google_requests
from google.oauth2 import id_token as google_id_token
from werkzeug.security import check_password_hash, generate_password_hash
from werkzeug.utils import secure_filename

from models import CarLocation, Favorite, User, db

BASE_DIR = os.path.abspath(os.path.dirname(__file__))
UPLOAD_DIR = os.path.join(BASE_DIR, "uploads")
os.makedirs(UPLOAD_DIR, exist_ok=True)

app = Flask(__name__)
app.config["SQLALCHEMY_DATABASE_URI"] = "sqlite:///" + os.path.join(BASE_DIR, "parking.db")
app.config["SQLALCHEMY_TRACK_MODIFICATIONS"] = False
app.config["SECRET_KEY"] = os.environ.get("SECRET_KEY", "dev-secret-change-me")
app.config["MAX_CONTENT_LENGTH"] = 8 * 1024 * 1024  # 8 MB, per le foto del posto auto

# Web Client ID del progetto Firebase (Task 6): è la "audience" attesa nei token Google
# che l'app Android manda qui per il login/registrazione veloce con Google.
GOOGLE_WEB_CLIENT_ID = os.environ.get("GOOGLE_WEB_CLIENT_ID", "")

db.init_app(app)

with app.app_context():
    db.create_all()

TOKEN_TTL_DAYS = 7
ALLOWED_PHOTO_EXTENSIONS = {"jpg", "jpeg", "png"}


def generate_token(user_id: int) -> str:
    payload = {
        "sub": user_id,
        "exp": datetime.now(timezone.utc) + timedelta(days=TOKEN_TTL_DAYS),
    }
    return jwt.encode(payload, app.config["SECRET_KEY"], algorithm="HS256")


def token_required(f):
    """Valida l'header 'Authorization: Bearer <token>' su tutte le rotte protette."""

    @wraps(f)
    def decorated(*args, **kwargs):
        auth_header = request.headers.get("Authorization", "")
        if not auth_header.startswith("Bearer "):
            return jsonify({"error": "Missing or malformed Authorization header"}), 401

        token = auth_header.split(" ", 1)[1]
        try:
            payload = jwt.decode(token, app.config["SECRET_KEY"], algorithms=["HS256"])
        except jwt.ExpiredSignatureError:
            return jsonify({"error": "Token expired"}), 401
        except jwt.InvalidTokenError:
            return jsonify({"error": "Invalid token"}), 401

        user = db.session.get(User, payload["sub"])
        if user is None:
            return jsonify({"error": "User not found"}), 401

        g.current_user = user
        return f(*args, **kwargs)

    return decorated


def allowed_photo(filename: str) -> bool:
    return "." in filename and filename.rsplit(".", 1)[1].lower() in ALLOWED_PHOTO_EXTENSIONS


# ---------------------------------------------------------------------------
# Auth
# ---------------------------------------------------------------------------

@app.route("/auth/register", methods=["POST"])
def register():
    data = request.get_json(silent=True) or {}
    email = (data.get("email") or "").strip().lower()
    password = data.get("password") or ""

    if not email or not password:
        return jsonify({"error": "email and password are required"}), 400
    if len(password) < 6:
        return jsonify({"error": "password must be at least 6 characters"}), 400
    if User.query.filter_by(email=email).first() is not None:
        return jsonify({"error": "email already registered"}), 409

    user = User(email=email, password_hash=generate_password_hash(password))
    db.session.add(user)
    db.session.commit()

    # Login automatico dopo la registrazione: l'app non deve rimandare l'utente alla
    # schermata di accesso, il token gli permette di entrare subito.
    return jsonify({"id": user.id, "email": user.email, "token": generate_token(user.id)}), 201


@app.route("/auth/login", methods=["POST"])
def login():
    data = request.get_json(silent=True) or {}
    email = (data.get("email") or "").strip().lower()
    password = data.get("password") or ""

    user = User.query.filter_by(email=email).first()
    # user.password_hash è None per gli account creati via Google: niente password da
    # controllare, devono usare /auth/google.
    if user is None or user.password_hash is None or not check_password_hash(user.password_hash, password):
        return jsonify({"error": "invalid email or password"}), 401

    return jsonify({"token": generate_token(user.id)}), 200


@app.route("/auth/google", methods=["POST"])
def google_auth():
    """Login/registrazione rapida con Google: l'app manda l'ID token ottenuto da Credential
    Manager, qui lo verifico con Google e creo l'utente al primo accesso (find-or-create),
    poi rilascio il nostro JWT come al login normale."""
    data = request.get_json(silent=True) or {}
    google_token = data.get("id_token") or ""
    if not google_token:
        return jsonify({"error": "id_token is required"}), 400

    try:
        payload = google_id_token.verify_oauth2_token(
            google_token, google_requests.Request(), GOOGLE_WEB_CLIENT_ID
        )
    except ValueError:
        return jsonify({"error": "invalid Google token"}), 401

    email = (payload.get("email") or "").strip().lower()
    if not email:
        return jsonify({"error": "Google account has no email"}), 400
    if not payload.get("email_verified", False):
        return jsonify({"error": "Google email not verified"}), 401

    user = User.query.filter_by(email=email).first()
    if user is None:
        user = User(email=email, password_hash=None)
        db.session.add(user)
        db.session.commit()

    return jsonify({"token": generate_token(user.id), "email": user.email}), 200


@app.route("/account", methods=["GET"])
@token_required
def get_account():
    return jsonify(g.current_user.to_dict()), 200


@app.route("/account", methods=["PUT"])
@token_required
def update_account():
    """Aggiorna nome, cognome, indirizzo di casa e (opzionale) foto profilo.
    Stesso schema di save_car_location: JSON puro oppure multipart/form-data se
    c'è una foto da caricare."""
    user = g.current_user

    if request.content_type and request.content_type.startswith("multipart/form-data"):
        first_name = request.form.get("first_name")
        last_name = request.form.get("last_name")
        home_address = request.form.get("home_address")
        photo = request.files.get("photo")

        if photo and photo.filename and allowed_photo(photo.filename):
            filename = secure_filename(
                f"user{user.id}_profile_{int(datetime.now().timestamp())}_{photo.filename}"
            )
            photo.save(os.path.join(UPLOAD_DIR, filename))
            user.photo_url = f"/uploads/{filename}"
    else:
        data = request.get_json(silent=True) or {}
        first_name = data.get("first_name")
        last_name = data.get("last_name")
        home_address = data.get("home_address")

    user.first_name = first_name
    user.last_name = last_name
    user.home_address = home_address
    db.session.commit()

    return jsonify(user.to_dict()), 200


@app.route("/account", methods=["DELETE"])
@token_required
def delete_account():
    """Elimina definitivamente l'account e tutti i suoi dati. Le righe di Favorite e
    CarLocation sono in cascade sulla relazione (vedi models.py), quindi cancellare
    l'utente basta a cancellare anche loro."""
    db.session.delete(g.current_user)
    db.session.commit()
    return "", 204


# ---------------------------------------------------------------------------
# Favorites
# ---------------------------------------------------------------------------

@app.route("/favorites", methods=["GET"])
@token_required
def get_favorites():
    favorites = Favorite.query.filter_by(user_id=g.current_user.id).all()
    return jsonify([f.to_dict() for f in favorites]), 200


@app.route("/favorites", methods=["POST"])
@token_required
def add_favorite():
    data = request.get_json(silent=True) or {}
    parking_id = data.get("parking_id")
    if parking_id is None:
        return jsonify({"error": "parking_id is required"}), 400

    existing = Favorite.query.filter_by(
        user_id=g.current_user.id, parking_id=parking_id
    ).first()
    if existing is None:
        db.session.add(Favorite(user_id=g.current_user.id, parking_id=parking_id))
        db.session.commit()

    return jsonify({"parking_id": parking_id}), 201


@app.route("/favorites/<int:parking_id>", methods=["DELETE"])
@token_required
def delete_favorite(parking_id):
    favorite = Favorite.query.filter_by(
        user_id=g.current_user.id, parking_id=parking_id
    ).first()
    if favorite is None:
        return jsonify({"error": "favorite not found"}), 404

    db.session.delete(favorite)
    db.session.commit()
    return "", 204


# ---------------------------------------------------------------------------
# Car location
# ---------------------------------------------------------------------------

@app.route("/car-location", methods=["GET"])
@token_required
def get_car_location():
    location = (
        CarLocation.query.filter_by(user_id=g.current_user.id)
        .order_by(CarLocation.saved_at.desc())
        .first()
    )
    if location is None:
        return jsonify({"error": "no car location saved"}), 404
    return jsonify(location.to_dict()), 200


@app.route("/car-location", methods=["POST"])
@token_required
def save_car_location():
    """Salva la posizione dell'auto. Accetta sia JSON puro sia multipart/form-data
    con una foto opzionale (campo 'photo'), come richiesto dal Task 5 lato Android."""
    photo_url = None

    if request.content_type and request.content_type.startswith("multipart/form-data"):
        lat = request.form.get("lat")
        lng = request.form.get("lng")
        photo = request.files.get("photo")

        if photo and photo.filename and allowed_photo(photo.filename):
            filename = secure_filename(
                f"user{g.current_user.id}_{int(datetime.now().timestamp())}_{photo.filename}"
            )
            photo.save(os.path.join(UPLOAD_DIR, filename))
            photo_url = f"/uploads/{filename}"
    else:
        data = request.get_json(silent=True) or {}
        lat = data.get("lat")
        lng = data.get("lng")

    if lat is None or lng is None:
        return jsonify({"error": "lat and lng are required"}), 400

    try:
        lat = float(lat)
        lng = float(lng)
    except (TypeError, ValueError):
        return jsonify({"error": "lat and lng must be numbers"}), 400

    location = CarLocation(user_id=g.current_user.id, lat=lat, lng=lng, photo_url=photo_url)
    db.session.add(location)
    db.session.commit()

    return jsonify(location.to_dict()), 201


@app.route("/car-location", methods=["DELETE"])
@token_required
def delete_car_location():
    deleted = CarLocation.query.filter_by(user_id=g.current_user.id).delete()
    db.session.commit()
    if deleted == 0:
        return jsonify({"error": "no car location saved"}), 404
    return "", 204


@app.route("/uploads/<path:filename>")
def uploaded_file(filename):
    return send_from_directory(UPLOAD_DIR, filename)


@app.route("/health")
def health():
    return jsonify({"status": "ok"}), 200


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5000, debug=True)
