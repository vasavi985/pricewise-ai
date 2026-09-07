import React, { useState, useEffect } from "react";
import { Link, useNavigate, useLocation } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import logoImg from "../assets/logos/pricewise-ai-logo-option-6.png";
import { FaEye, FaEyeSlash, FaSpinner, FaExclamationCircle } from "react-icons/fa";
import "../styles/login.css";

function Login() {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState("");
  const [submitting, setSubmitting] = useState(false);

  const { login, currentUser } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  const redirectPath = location.state?.from?.pathname || "/history";
  const stateMessage = location.state?.message || "";

  useEffect(() => {
    if (currentUser) {
      navigate(redirectPath, { replace: true });
    }
  }, [currentUser, navigate, redirectPath]);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError("");

    if (!email.trim() || !password) {
      setError("Please enter both email and password.");
      return;
    }

    setSubmitting(true);
    try {
      await login(email.trim(), password);
      navigate(redirectPath, { replace: true });
    } catch (err) {
      let friendlyMessage = "Failed to sign in. Please verify your credentials.";
      if (
        err.code === "auth/invalid-credential" ||
        err.code === "auth/wrong-password" ||
        err.code === "auth/user-not-found"
      ) {
        friendlyMessage = "Incorrect email or password. Please try again.";
      } else if (err.code === "auth/invalid-email") {
        friendlyMessage = "Please provide a valid email address.";
      } else if (err.code === "auth/too-many-requests") {
        friendlyMessage = "Too many failed attempts. Please try again in a few moments.";
      }
      setError(friendlyMessage);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="auth-page">
      <div className="auth-container">
        <div className="auth-card">
          <div className="auth-brand">
            <Link to="/">
              <img src={logoImg} alt="PriceWise AI" className="auth-logo" />
            </Link>
            <h1 className="auth-title">Welcome Back</h1>
            <p className="auth-subtitle">Log in to view your personalized search history &amp; trackings</p>
          </div>

          {stateMessage && !error && (
            <div className="auth-error-banner" style={{ background: "#EEF2FF", borderColor: "#C7D2FE", color: "#4338CA" }}>
              <FaExclamationCircle />
              <span>{stateMessage}</span>
            </div>
          )}

          {error && (
            <div className="auth-error-banner">
              <FaExclamationCircle />
              <span>{error}</span>
            </div>
          )}

          <form onSubmit={handleSubmit} className="auth-form">
            <div className="auth-input-group">
              <label htmlFor="login-email">Email Address</label>
              <div className="auth-input-wrapper">
                <input
                  id="login-email"
                  type="email"
                  placeholder="name@example.com"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  autoComplete="email"
                  required
                />
              </div>
            </div>

            <div className="auth-input-group">
              <label htmlFor="login-password">Password</label>
              <div className="auth-input-wrapper">
                <input
                  id="login-password"
                  type={showPassword ? "text" : "password"}
                  placeholder="Enter your password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  autoComplete="current-password"
                  required
                />
                <button
                  type="button"
                  className="auth-toggle-pwd"
                  onClick={() => setShowPassword(!showPassword)}
                  aria-label={showPassword ? "Hide password" : "Show password"}
                >
                  {showPassword ? <FaEyeSlash /> : <FaEye />}
                </button>
              </div>
            </div>

            <button type="submit" className="auth-submit-btn" disabled={submitting}>
              {submitting ? (
                <>
                  <FaSpinner className="auth-spin-icon" /> Signing in...
                </>
              ) : (
                "Sign In"
              )}
            </button>
          </form>

          <div className="auth-footer-prompt">
            Don't have an account?
            <Link to="/register" state={{ from: location.state?.from }}>Create one</Link>
          </div>
        </div>
      </div>
    </div>
  );
}

export default Login;
