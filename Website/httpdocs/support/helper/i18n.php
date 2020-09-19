<?php

class i18n {
    private static $locale = null;
    private static $files = null;
    private static $dictionary = null;
    public static $root =  null;
    public static $default_locale = null;

    private static function resolve($locale) {
        $r = array(
            'locale' => explode('-', strtolower($locale)),
            'files' => array()
        );
        if (isset($r['locale'][0])) {
          $f = strtolower(self::$root.'/'.$r['locale'][0].'.json');
          if (file_exists($f)) {
                if (is_link($f)) {
                    array_push($r['files'], self::$root.'/'.readlink($f));
                    if (!isset($r['locale'][1])) {
                        $r['locale'] = explode('-', strtolower(basename($r['files'][0],'.json')));
                    }
                }
                else {
                    array_push($r['files'], $f);
                }
            }
        }
        if (isset($r['locale'][1])) {
            $file = strtolower(self::$root.'/'.$r['locale'][0].'-'.$r['locale'][1].'.json'); 
            if (($file != $r['files'][0]) && file_exists($file))
                array_push($r['files'], $file);
        }
        return $r;
    }

    public static function detect() {
        $r = self::resolve(self::$default_locale);
        self::$locale = $r['locale'];
        self::$files = $r['files'];
        $quality = -1;
        if (isset($_SERVER['HTTP_ACCEPT_LANGUAGE'])) {
            preg_match_all('/([^;,-]+-?[^;,]*)(?:;q=([\d.]+))?(?:$|,)/', $_SERVER['HTTP_ACCEPT_LANGUAGE'], $matches);
            for ($i = 0; $i < count($matches[0]); $i++) {
                $r = self::resolve($matches[1][$i]); 
                if (!empty($r['files'])) {
                    $q = empty($matches[3][$i])?1.0:floatval($matches[3][$i]);
                    if ($q > $quality) {
                        self::$locale = $r['locale'];
                        self::$files = $r['files'];
                        if ($q >= 1)
                            break;
                        else
                            $quality = $q;
                    }
                }
            }
        }
    }

    public static function locale($style = 'html') {
        if (self::$locale === null)
            self::detect();
        switch ($style) {
            case 'html':
                return self::$locale[0].(isset(self::$locale[1])?'-'.self::$locale[1]:'');
            case 'cldr':
                return self::$locale[0].(isset(self::$locale[1])?'_'.strtoupper(self::$locale[1]):'');
            case 'primary':
                return self::$locale[0];
            default:
                die('Unknown i18n locale style: '.$style);
        }
    }
     
    public static function t($identifier) {
        if (self::$locale === null)
            self::detect();
        if (self::$dictionary === null) {
            self::$dictionary = array();
            foreach (self::$files as $file) {
                $d = json_decode(file_get_contents($file), true);
                self::$dictionary = $d + self::$dictionary;
            }
        }
        return isset(self::$dictionary[$identifier]) ? self::$dictionary[$identifier] : $identifier;
    }

    public static function l($value) {
        self::detect();
        trigger_error('i18n localization has not been implemented yet', E_USER_WARNING);
        return $value;
    }
}

i18n::$root = dirname(__FILE__);
i18n::$default_locale = 'en-us';

?>
