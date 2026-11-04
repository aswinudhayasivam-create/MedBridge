"""Consistent JSON error shape: {"error": {"code": ..., "message": ...}}."""


class ApiError(Exception):
    def __init__(self, status, code, message):
        super().__init__(message)
        self.status = status
        self.code = code
        self.message = message


def register_error_handlers(app):
    from flask import jsonify

    @app.errorhandler(ApiError)
    def _api_error(err):
        return jsonify({"error": {"code": err.code, "message": err.message}}), err.status

    @app.errorhandler(404)
    def _not_found(err):
        return jsonify({"error": {"code": "not_found", "message": "Resource not found."}}), 404

    @app.errorhandler(405)
    def _method(err):
        return jsonify({"error": {"code": "method_not_allowed", "message": "Method not allowed."}}), 405

    @app.errorhandler(500)
    def _internal(err):
        return jsonify({"error": {"code": "internal_error", "message": "Something went wrong."}}), 500
