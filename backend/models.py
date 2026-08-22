from datetime import datetime, timezone

from flask_sqlalchemy import SQLAlchemy

db = SQLAlchemy()


def utcnow() -> datetime:
    return datetime.now(timezone.utc)


class User(db.Model):
    __tablename__ = "users"

    id = db.Column(db.Integer, primary_key=True)
    email = db.Column(db.String(255), unique=True, nullable=False, index=True)
    # Null per gli account creati via Google Sign-In (nessuna password nostra da controllare)
    password_hash = db.Column(db.String(255), nullable=True)
    created_at = db.Column(db.DateTime, default=utcnow, nullable=False)

    favorites = db.relationship(
        "Favorite", backref="user", cascade="all, delete-orphan", lazy=True
    )
    car_locations = db.relationship(
        "CarLocation", backref="user", cascade="all, delete-orphan", lazy=True
    )


class Favorite(db.Model):
    __tablename__ = "favorites"
    __table_args__ = (
        db.UniqueConstraint("user_id", "parking_id", name="uq_favorite_user_parking"),
    )

    id = db.Column(db.Integer, primary_key=True)
    user_id = db.Column(db.Integer, db.ForeignKey("users.id"), nullable=False)
    parking_id = db.Column(db.BigInteger, nullable=False)
    created_at = db.Column(db.DateTime, default=utcnow, nullable=False)

    def to_dict(self):
        return {
            "parking_id": self.parking_id,
            "created_at": self.created_at.isoformat(),
        }


class CarLocation(db.Model):
    __tablename__ = "car_locations"

    id = db.Column(db.Integer, primary_key=True)
    user_id = db.Column(db.Integer, db.ForeignKey("users.id"), nullable=False)
    lat = db.Column(db.Float, nullable=False)
    lng = db.Column(db.Float, nullable=False)
    photo_url = db.Column(db.String(512), nullable=True)
    saved_at = db.Column(db.DateTime, default=utcnow, nullable=False)

    def to_dict(self):
        return {
            "lat": self.lat,
            "lng": self.lng,
            "photo_url": self.photo_url,
            "saved_at": self.saved_at.isoformat(),
        }
