export default function MissingConfig() {
  return (
    <main className="center">
      <section className="card login">
        <h1>Setup needed</h1>
        <p>
          Set <code>VITE_GOOGLE_CLIENT_ID</code> in <code>frontend/.env</code> (see <code>.env.example</code>), then
          restart the dev server.
        </p>
      </section>
    </main>
  )
}
