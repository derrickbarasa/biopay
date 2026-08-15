# Landing hero photos

The landing page hero (`src/pages/LandingPage.vue`) rotates through five slides.
Each slide shows a photo from this folder. Until a file exists, the hero frame
shows a light teal placeholder gradient (so the page never looks broken).

Drop in these five files (landscape, ~1200×900, JPG). Map them from the
composite image you provided as follows:

| File name                     | Slide            | Which tile of the composite to use            |
|-------------------------------|------------------|-----------------------------------------------|
| `cash-transfers.jpg`          | Cash transfers   | Phone "Transaction Receipt — Payment Successful" (bottom middle) |
| `voucher-redemption.jpg`      | Voucher redemption | Hand holding the "BioPay VOUCHER" card at the market (middle right) |
| `biometric-verification.jpg`  | Biometric verification | Face-verification kiosk "Verification Successful" (middle left) |
| `deduplication.jpg`           | Deduplication    | Laptop "FRAUD PREVENTED — Duplicate registration detected" (top right) |
| `ai-agent.jpg`                | AI agent / oversight | Desktop dashboard on the monitor (top left)  |

Unused tiles (fingerprint "SCAN TO PAY" device, and the elderly woman receiving
a voucher) aren't needed for the hero, but you can swap any mapping above if you
prefer a different photo for a slide — just keep the file names.

## How to crop
Open the composite in any image editor (Photos, Paint, Photoshop, GIMP, or an
online cropper), select each tile, and export it with the file name above into
this folder. Photos are used as `background-size: cover`, so exact dimensions
don't matter — landscape crops look best.
