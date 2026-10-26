/** Cache-buster for the captcha image; the response also sets the CAPTCHA_KEY cookie. */
export function newCaptchaUrl(): string {
  return `/api/auth/captcha?t=${Date.now()}`
}
