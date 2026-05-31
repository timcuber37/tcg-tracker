from flask import Flask
import config
import auth

app = Flask(__name__)
app.secret_key = config.FLASK_SECRET_KEY

from routes.command_routes import command_bp
from routes.query_routes   import query_bp
from routes.image_routes   import image_bp
from routes.auth_routes    import auth_bp

app.register_blueprint(command_bp)
app.register_blueprint(query_bp)
app.register_blueprint(image_bp)
app.register_blueprint(auth_bp)


@app.context_processor
def inject_user_context():
    return {"current_user": auth.current_user()}


if __name__ == "__main__":
    app.run(debug=config.FLASK_DEBUG)
