xquery version "1.0";

import module namespace xdb="http://exist-db.org/xquery/xmldb";

(: The following external variables are set by the repo:deploy function :)

(: file path pointing to the exist installation directory :)
declare variable $home external;
(: path to the directory containing the unpacked .xar package :)
declare variable $dir external;
(: the target collection into which the app is deployed :)
declare variable $target external;

declare function local:mkcol-recursive($collection, $components) {
    if (exists($components)) then
        let $newColl := concat($collection, "/", $components[1])
        return (
            xdb:create-collection($collection, $components[1]),
            local:mkcol-recursive($newColl, subsequence($components, 2))
        )
    else
        ()
};

(: Helper function to recursively create a collection hierarchy. :)
declare function local:mkcol($collection, $path) {
    local:mkcol-recursive($collection, tokenize($path, "/"))
};


declare function local:fix-security() {
let $security-config := doc("/db/system/security/config.xml")
let $jmmc-realm := <realm id="JMMC" xmlns="http://exist-db.org/Configuration"> <url>https://apps.jmmc.fr/account/manage.php</url> </realm>
    
let $jar-name:="exist-security-jmmc.jar"
let $jar-present:= file:exists(concat(system:get-exist-home(),"/lib/extensions/",$jar-name))
let $security-config-set := exists($security-config//realm[@id="JMMC"])

return if ($security-config-set and $jar-present) then
        "All is fine : security config is set and jar is present."
        else if($jar-present) then
        (
         "Jar present.",
         update insert $jmmc-realm into $security-config/* ,
         "JMMC realm just appent to security manager, please reboot existdb"
        )
    else if($security-config-set) then
        "Oups: security config set but jar is not present"
    else 
        "coucou"
};


(: store the collection configuration :)
local:mkcol("/db/system/config", $target),
xdb:store-files-from-pattern(concat("/system/config", $target), $dir, "*.xconf"),
local:fix-security()


