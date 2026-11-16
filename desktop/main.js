const { app, BrowserWindow, shell } = require('electron');
const URL_APP = process.env.SMARTPRO_URL || 'https://massockadri-create.github.io/Smart-Pro/';
app.userAgentFallback = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36';
function createWindow() {
  const w = new BrowserWindow({ width: 1280, height: 800, title: 'Smart Pro', autoHideMenuBar: true });
  w.webContents.setWindowOpenHandler(({ url }) => {
    if (url === 'about:blank') return { action: 'allow' };
    if (/accounts\.google\.com|firebaseapp\.com/.test(url)) return { action: 'allow', overrideBrowserWindowOptions: { width: 520, height: 700, autoHideMenuBar: true } };
    shell.openExternal(url);
    return { action: 'deny' };
  });
  w.loadURL(URL_APP);
}
app.whenReady().then(createWindow);
app.on('window-all-closed', () => app.quit());
