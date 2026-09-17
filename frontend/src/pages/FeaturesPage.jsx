/**
 * Standalone "Features" page describing the dynamic {{...}} variables the
 * request editor supports. Reached via the link in BodyEditor's hint and
 * addressable directly at #/features; links back to the main app.
 */
export default function FeaturesPage() {
  return (
    <div className="app features-page">
      <header className="app__topbar">
        <div className="app__brand">
          <span className="app__logo">cURL GUI</span>
          <span className="app__tag">Features</span>
        </div>
        <a className="hint-link" href="#/">
          &larr; Back to app
        </a>
      </header>

      <div className="features-page__content">
        <h1 className="features-page__title">Dynamic variables</h1>
        <p className="features-page__intro">
          Beyond <code>{'{{ENVIRONMENT_VARIABLES}}'}</code>, the URL, headers, cookies
          and body accept two <em>dynamic</em> templates that are resolved fresh,
          immediately before each request is sent, rather than once up front.
          Every occurrence is substituted independently, and the request you see in
          the editor always keeps the literal template - only the copy that is
          actually sent gets a resolved value, so Run Multiple and Chain can reuse
          the same request on every iteration.
        </p>

        <section className="panel features-page__feature">
          <div className="panel__header">
            <h2 className="panel__title">Random value - {'{{random(N)}}'}</h2>
          </div>
          <p>
            Produces a fresh random alphanumeric string of exactly <code>N</code>{' '}
            characters (letters and digits only, no punctuation) on every request
            sent.
          </p>
          <ul className="features-page__list">
            <li>Works anywhere: URL, headers, cookies, and the body.</li>
            <li>
              <code>N</code> must be a whole number from 1 to 4096.
            </li>
            <li>
              Each occurrence gets its own independent value - <code>{'{{random(8)}}-{{random(8)}}'}</code>{' '}
              never repeats the first half in the second.
            </li>
            <li>
              In a loop or chain, every iteration and every dispatch gets a newly
              generated value.
            </li>
          </ul>
          <div className="features-page__example">
            <code>{'{"session": "{{random(20)}}"}'}</code>
          </div>
        </section>

        <section className="panel features-page__feature">
          <div className="panel__header">
            <h2 className="panel__title">Incrementing counter - {'{{increment(N)}}'}</h2>
          </div>
          <p>
            Produces a counter that starts at <code>N</code> and goes up by 1 on
            every iteration of a Run Multiple loop or a Chain run.
          </p>
          <ul className="features-page__list">
            <li>Works anywhere: URL, headers, cookies, and the body.</li>
            <li>
              <code>N</code> is the starting value - it can be zero or negative, e.g.{' '}
              <code>{'{{increment(0)}}'}</code> or <code>{'{{increment(-3)}}'}</code>.
            </li>
            <li>
              Every occurrence of <code>{'{{increment(N)}}'}</code> with the same{' '}
              <code>N</code> in one request shares the same value for that
              iteration, so a repeated reference stays in sync.
            </li>
            <li>
              On a plain Send (outside any loop or chain) it always resolves to its
              literal start value <code>N</code>, since there is only one iteration.
            </li>
            <li>In a Chain, every request within the same loop iteration sees that iteration's value.</li>
          </ul>
          <div className="features-page__example">
            <code>{'{"orderId": {{increment(1000)}}}'}</code>
          </div>
        </section>

        <section className="panel features-page__feature">
          <div className="panel__header">
            <h2 className="panel__title">Combining them</h2>
          </div>
          <p>
            Both templates can appear together, and alongside environment
            variables, in the same field:
          </p>
          <div className="features-page__example">
            <code>{'{"id": {{increment(1)}}, "nonce": "{{random(12)}}", "env": "{{API_URL}}"}'}</code>
          </div>
        </section>
      </div>
    </div>
  )
}
