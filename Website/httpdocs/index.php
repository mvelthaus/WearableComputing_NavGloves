<?php
    // INFO Navigation controller

    // NOTE The view is usually loaded by the navigation controller, but we also
    //      load it via PHP to serve browsers and bots without JavaScript support
    //      the same content.
    preg_match('/^\/([^\?#]*[^\/\?#])?\/?(?:\?([^#]*))?(?:#(.*))?/', $_SERVER['REQUEST_URI'], $nav_url);
    $nav_view = strtolower($nav_url[1]);
    $nav_file = './views/'.$nav_view.'.php';
    if (!file_exists($nav_file)) {
        http_response_code(404);
        die();
    }

    // INFO OpenGraph

    // NOTE Protocol and hostname for Open Graph URLs.
    $og_url = 'https://'.preg_replace('/^(?:www|en|de)\./', '', $_SERVER['SERVER_NAME']).$_SERVER['REQUEST_URI'];
    $og_image = 'https://'.preg_replace('/^(?:www|en|de)\./', '', $_SERVER['SERVER_NAME'])."/assets/opengraph/default.png";
?>

<!DOCTYPE html>
<html lang="en_GB">

<head>
    <meta name="viewport" content="initial-scale=1, maximum-scale=1">
    <meta name="twitter:card" content="summary_large_image">
    <meta name="twitter:site" content="flowyapps">
    <meta property="og:type" content="website">
    <meta property="og:title" content="NavGloves — Explore freely">
    <meta property="og:description" content="NavGloves guide you, while you focus on the important things.">
    <meta property="og:image" content="<?=$og_image?>">
    <meta property="og:url" content="<?=$og_url?>">
    <meta property="og:locale" content="en_GB">
    <title>NavGloves — Explore freely</title>
    <style>
        @import url("/styles/generic.css");
        @import url("/styles/layout.css");
        @import url("/styles/site.css");
        @import url("/styles/snippets.css");
        @import url('/styles/views/contact.css');
        @import url('/styles/views/details.css');
        @import url('/styles/views/home.css');
        @import url('/styles/views/jobs.css');
        @import url('/styles/views/offers.css');
        @import url('/styles/views/press.css');
        @import url('/styles/views/story.css');
    </style>
    <script>
        self.Honey = { 'requirePath':['/modules/'] };
    </script>
    <script src="/support/honey/honey.js" async></script>
</head>

<body>
    <div id="Viewport">
        <?php include($nav_file); ?>
    </div>
</body>

</html>
