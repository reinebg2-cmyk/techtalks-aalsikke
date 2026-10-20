function getToken() {
  return sessionStorage.getItem('aalsikke_token') || localStorage.getItem('aalsikke_token');
}

function clearToken() {
  sessionStorage.removeItem('aalsikke_token');
  localStorage.removeItem('aalsikke_token');
}

function setToken(token) {
  const storage = sessionStorage.getItem('aalsikke_token') ? sessionStorage : localStorage;
  storage.setItem('aalsikke_token', token);
}