import React, { useState, useEffect } from "react";
import { Link, useNavigate, useLocation } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import logoImg from "../assets/logos/pricewise-ai-logo-option-6.png";
import { FaEye, FaEyeSlash, FaSpinner, FaExclamationCircle } from "react-icons/fa";
import "../styles/login.css";

function Register() {
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState("");
  const [submitting, setSubmitting] = useState(false);

  const { register, currentUser } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  const redirectPath = location.state?.from?.pathname || "/history";

  useEffect(() => {
    if (currentUser) {
      navigate(redirectPath, { replace: true });
    }
  }, [currentUser, navigate, redirectPath]);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError("");

    if (!name.trim()) {
      setError("Please enter your name.");
      return;
    }

    if (!email.trim()) {
      setError("Please enter your email address.");
      return;
    }

    if (password.length < 6) {
      setError("Password must be at least 6 characters long.");
      return;
    }

    if (password !== confirmPassword) {
      setError("Passwords do not match.");
      return;
    }

    setSubmitting(true);
    try {
      await register(email.trim(), password, name.trim());
      navigate(redirectPath, { replace: true });
    } catch (err) {
      let friendlyMessage = "Failed to create account. Please try again.";
      if (err.code === "auth/email-already-in-use") {
        friendlyMessage = "An account with this email already exists. Please log in.";
      } else if (err.code === "auth/weak-password") {
        friendlyMessage = "Password is too weak. Please use at least 6 characters.";
      } else if (err.code === "auth/invalid-email") {
        friendlyMessage = "Please enter a valid email address.";
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
            <h1 className="auth-title">Create Account</h1>
            <p className="auth-subtitle">Join PriceWise AI to track prices &amp; save search history</p>
          </div>

          {error && (
            <div className="auth-error-banner">
              <FaExclamationCircle />
              <span>{error}</span>
            </div>
          )}

          <form onSubmit={handleSubmit} className="auth-form">
            <div className="auth-input-group">
              <label htmlFor="reg-name">Full Name</label>
              <div className="auth-input-wrapper">
                <input
                  id="reg-name"
                  type="text"
                  placeholder="Your Name"
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  autoComplete="name"
                  required
                />
              </div>
            </div>

            <div className="auth-input-group">
              <label htmlFor="reg-email">Email Address</label>
              <div className="auth-input-wrapper">
                <input
                  id="reg-email"
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
              <label htmlFor="reg-password">Password</label>
              <div className="auth-input-wrapper">
                <input
                  id="reg-password"
                  type={showPassword ? "text" : "password"}
                  placeholder="Minimum 6 characters"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  autoComplete="new-password"
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

            <div className="auth-input-group">
              <label htmlFor="reg-confirm-password">Confirm Password</label>
              <div className="auth-input-wrapper">
                <input
                  id="reg-confirm-password"
                  type={showPassword ? "text" : "password"}
                  placeholder="Re-enter your password"
                  value={confirmPassword}
                  onChange={(e) => setConfirmPassword(e.target.value)}
                  autoComplete="new-password"
                  required
                />
              </div>
            </div>

            <button type="submit" className="auth-submit-btn" disabled={submitting}>
              {submitting ? (
                <>
                  <FaSpinner className="auth-spin-icon" /> Creating Account...
                </>
              ) : (
                "Create Account"
              )}
            </button>
          </form>

          <div className="auth-footer-prompt">
            Already have an account?
            <Link to="/login" state={{ from: location.state?.from }}>Sign in</Link>
          </div>
        </div>
      </div>
    </div>
  );
}

export default Register;
