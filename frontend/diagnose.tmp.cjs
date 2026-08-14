const { chromium } = require('playwright');

(async () => {
  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: 1400, height: 900 } });

  const consoleMsgs = [];
  page.on('console', (msg) => consoleMsgs.push(`[console:${msg.type()}] ${msg.text()}`));
  page.on('pageerror', (err) => consoleMsgs.push(`[pageerror] ${err.message}`));
  page.on('requestfailed', (req) => consoleMsgs.push(`[requestfailed] ${req.url()} - ${req.failure()?.errorText}`));

  console.log('--- Navigating to landing page ---');
  await page.goto('http://localhost:5173/', { waitUntil: 'networkidle' });
  await page.screenshot({ path: 'C:/Users/PC/AppData/Local/Temp/claude/e--Projects-claude-biopay/513a2169-e18f-497d-9115-3e0f967d88c1/scratchpad/01-landing.png' });

  console.log('--- Navigating to login ---');
  await page.goto('http://localhost:5173/login', { waitUntil: 'networkidle' });
  await page.screenshot({ path: 'C:/Users/PC/AppData/Local/Temp/claude/e--Projects-claude-biopay/513a2169-e18f-497d-9115-3e0f967d88c1/scratchpad/02-login.png' });

  console.log('--- Logging in as organization admin ---');
  await page.getByRole('button', { name: /Organization/ }).click();
  await page.getByLabel('Email').fill('admin@brightfuture.org');
  await page.getByRole('textbox', { name: 'Password' }).fill('ChangeMe123!');
  await page.getByRole('button', { name: 'Sign in' }).click();
  await page.waitForURL('**/app/dashboard', { timeout: 10000 }).catch((e) => console.log('URL wait failed:', e.message));
  await page.waitForTimeout(1500);
  await page.screenshot({ path: 'C:/Users/PC/AppData/Local/Temp/claude/e--Projects-claude-biopay/513a2169-e18f-497d-9115-3e0f967d88c1/scratchpad/03-dashboard.png', fullPage: true });
  console.log('Current URL:', page.url());

  console.log('--- Trying nav drawer item click (Households) ---');
  const householdsLink = page.getByRole('link', { name: 'Households' }).first();
  const beforeUrl = page.url();
  try {
    await householdsLink.click({ timeout: 5000 });
    await page.waitForTimeout(1000);
  } catch (e) {
    console.log('Click failed:', e.message);
  }
  console.log('URL before:', beforeUrl, 'URL after:', page.url());
  await page.screenshot({ path: 'C:/Users/PC/AppData/Local/Temp/claude/e--Projects-claude-biopay/513a2169-e18f-497d-9115-3e0f967d88c1/scratchpad/04-after-nav-click.png', fullPage: true });

  console.log('--- Trying profile menu click ---');
  try {
    const profileBtn = page.locator('.v-app-bar').getByRole('button').last();
    await profileBtn.click({ timeout: 5000 });
    await page.waitForTimeout(800);
    await page.screenshot({ path: 'C:/Users/PC/AppData/Local/Temp/claude/e--Projects-claude-biopay/513a2169-e18f-497d-9115-3e0f967d88c1/scratchpad/05-profile-menu.png' });
  } catch (e) {
    console.log('Profile menu click failed:', e.message);
  }

  console.log('\n--- Console/page messages ---');
  consoleMsgs.forEach((m) => console.log(m));

  await browser.close();
})();
