#[cfg(not(debug_assertions))]
compile_error!("AMS Desktop currently supports local development only");

fn is_local_frontend(url: &tauri::Url) -> bool {
    url.scheme() == "http"
        && url.host_str() == Some("localhost")
        && url.port_or_known_default() == Some(3000)
        && url.username().is_empty()
        && url.password().is_none()
}

fn main() {
    tauri::Builder::default()
        .setup(|app| {
            let url = "http://localhost:3000/".parse()?;
            tauri::WebviewWindowBuilder::new(app, "main", tauri::WebviewUrl::External(url))
                .title("AMS (local development)")
                .inner_size(1200.0, 800.0)
                .min_inner_size(320.0, 480.0)
                .on_navigation(is_local_frontend)
                .on_new_window(|_, _| tauri::webview::NewWindowResponse::Deny)
                .build()?;
            Ok(())
        })
        .run(tauri::generate_context!())
        .expect("AMS desktop development shell could not start");
}

#[cfg(test)]
mod tests {
    use super::is_local_frontend;

    #[test]
    fn only_exact_local_frontend_origin_is_allowed() {
        for url in [
            "http://localhost:3000/",
            "http://localhost:3000/login",
            "http://localhost:3000/settings?tab=security",
        ] {
            assert!(is_local_frontend(&url.parse().unwrap()), "{url}");
        }

        for url in [
            "https://localhost:3000/",
            "http://127.0.0.1:3000/",
            "http://localhost:3001/",
            "http://localhost.evil.test:3000/",
            "https://accounts.google.com/",
            "file:///C:/private.txt",
            "http://user@localhost:3000/",
        ] {
            assert!(!is_local_frontend(&url.parse().unwrap()), "{url}");
        }
    }
}
