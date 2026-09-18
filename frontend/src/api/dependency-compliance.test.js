import test from 'node:test'
import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __filename = fileURLToPath(import.meta.url)
const __dirname = path.dirname(__filename)
const frontendRoot = path.resolve(__dirname, '../..')
const repoRoot = path.resolve(frontendRoot, '..')

test('1. Dependency lockfile (package-lock.json) exists and is committed', () => {
  const lockfilePath = path.join(frontendRoot, 'package-lock.json')
  assert.ok(fs.existsSync(lockfilePath), 'package-lock.json must exist in frontend directory')

  const lockfileContent = fs.readFileSync(lockfilePath, 'utf8')
  const lockfile = JSON.parse(lockfileContent)
  assert.ok(lockfile.lockfileVersion >= 2, 'Lockfile version must be >= 2')
  assert.ok(lockfile.packages, 'Lockfile packages mapping must exist')
})

test('2. Direct frontend dependencies in package.json are locked and accounted for', () => {
  const packageJsonPath = path.join(frontendRoot, 'package.json')
  const lockfilePath = path.join(frontendRoot, 'package-lock.json')

  const pkg = JSON.parse(fs.readFileSync(packageJsonPath, 'utf8'))
  const lockfile = JSON.parse(fs.readFileSync(lockfilePath, 'utf8'))

  const directDeps = Object.keys(pkg.dependencies || {})
  assert.ok(directDeps.length > 0, 'Project must have direct dependencies')

  for (const dep of directDeps) {
    const key = `node_modules/${dep}`
    assert.ok(
      lockfile.packages[key] || lockfile.packages['']?.dependencies?.[dep],
      `Dependency ${dep} must be locked in package-lock.json`
    )
  }
})

test('3. index.html does not load remote CDNs, untrusted scripts, or external fonts', () => {
  const indexHtmlPath = path.join(frontendRoot, 'index.html')
  const html = fs.readFileSync(indexHtmlPath, 'utf8')

  // Prohibit external scripts, styles, and font CDNs
  const forbiddenPatterns = [
    /https?:\/\//i,
    /unpkg\.com/i,
    /cdnjs\.cloudflare\.com/i,
    /cdn\.jsdelivr\.net/i,
    /fonts\.googleapis\.com/i,
    /fonts\.gstatic\.com/i,
    /google-analytics\.com/i,
    /googletagmanager\.com/i,
    /connect\.facebook\.net/i,
  ]

  for (const pattern of forbiddenPatterns) {
    assert.strictEqual(
      pattern.test(html),
      false,
      `index.html must not contain external network pattern: ${pattern}`
    )
  }
})

test('4. Frontend source code does not reference backend secrets or private variables', () => {
  const srcDir = path.join(frontendRoot, 'src')

  function scanDir(dir) {
    const entries = fs.readdirSync(dir, { withFileTypes: true })
    for (const entry of entries) {
      const fullPath = path.join(dir, entry.name)
      if (entry.isDirectory()) {
        scanDir(fullPath)
      } else if (entry.isFile() && (entry.name.endsWith('.js') || entry.name.endsWith('.vue'))) {
        // Skip this compliance test file itself
        if (entry.name === 'dependency-compliance.test.js') continue

        const content = fs.readFileSync(fullPath, 'utf8')
        assert.strictEqual(
          content.includes('JWT_SECRET'),
          false,
          `File ${entry.name} must not reference JWT_SECRET`
        )
        assert.strictEqual(
          content.includes('DB_PASSWORD'),
          false,
          `File ${entry.name} must not reference DB_PASSWORD`
        )
        assert.strictEqual(
          content.includes('Shizukusan'),
          false,
          `File ${entry.name} must not contain sensitive password literals`
        )
      }
    }
  }

  scanDir(srcDir)
})

test('5. Frontend styling relies on local system fonts without remote @font-face or @import', () => {
  const styleCssPath = path.join(frontendRoot, 'src/style.css')
  const css = fs.readFileSync(styleCssPath, 'utf8')

  assert.strictEqual(css.includes('@import url('), false, 'style.css must not use remote @import url()')
  assert.strictEqual(css.includes('@font-face'), false, 'style.css must not declare external @font-face')
  assert.ok(css.includes('-apple-system'), 'style.css should specify system font fallback stack')
})

test('6. Repository gitignore properly excludes local env files and sensitive artifacts', () => {
  const gitignorePath = path.join(repoRoot, '.gitignore')
  assert.ok(fs.existsSync(gitignorePath), '.gitignore must exist in root')

  const gitignore = fs.readFileSync(gitignorePath, 'utf8')
  assert.ok(gitignore.includes('node_modules/'), '.gitignore must exclude node_modules')
  assert.ok(gitignore.includes('target/'), '.gitignore must exclude target')
  assert.ok(gitignore.includes('.env'), '.gitignore must exclude .env')
  assert.ok(gitignore.includes('frontend/.env'), '.gitignore must exclude frontend/.env')
  assert.ok(gitignore.includes('*.key'), '.gitignore must exclude private keys')
  assert.ok(gitignore.includes('backups/'), '.gitignore must exclude database backups')
})
