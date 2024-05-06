#!/usr/bin/env ruby

require 'erb'
require 'fileutils'
require 'yaml'
require 'rubygems'
require 'rdiscount'
include FileUtils

$root = File.expand_path(File.dirname(__FILE__))
# Where the source markdown files exist
$bookContent = "#{$root}"
# Where the figures exist
$bookFigures = "#{$root}/figures"
# Where the html, and ebooks are published
$outputDirectory = "#{$root}/target"
# Book generation configuration
$config = YAML.load_file("#$root/book.yml")['default']
$bookFileName = $config['bookFileName']
$texTemplateFile = <<'TEX'
\documentclass[a4paper]{book}
\usepackage[
	%urlbordercolor = {1 1 1},
	%linkbordercolor = {1 1 1},
	%citebordercolor = {1 1 1},
	urlcolor = blue,
	colorlinks = true,
	citecolor = black,
	linkcolor = black]{hyperref}
\usepackage{graphicx}
\usepackage{xltxtra}
\usepackage{fancyhdr}
\usepackage{booktabs}
\usepackage{indentfirst}
\usepackage{framed,color}
\usepackage{caption}
\usepackage{longtable}
\captionsetup{font=bf,position=below}

\usepackage{ctable}

\definecolor{shadecolor}{gray}{0.90}

\setromanfont[Mapping=tex-text,BoldFont=<%= config['bold'] %>]{<%= config['font'] %>}
\setmonofont[Scale=.85]{<%= config['mono'] %>}

\XeTeXlinebreaklocale{<%= lang %>}
<%= config['langrule'] %>

\settowidth{\parindent}{<%= config['indent'] %>}
\title{<%= config['bookTitle'] %>}
\newcommand{\shorttitle}{<%= config['bookShortTitle'] %>}
\author{<%= config['bookAuthor'] %>}


\makeatletter
\let\savedauthor=\@author
\let\savedtitle=\@title
\def\imgwidth{.6\linewidth}
\def\maxwidth{\ifdim\Gin@nat@width>\imgwidth\imgwidth
\else\Gin@nat@width\fi}
\makeatother

\title{\textbf{\savedtitle}}
\author{\textbf{\savedauthor}\thanks{<%= config['thanks'] %>}}
\def\w3cdtfymd{\the\year-\ifnum\month<10 0\fi\the\month-\ifnum\day<10 0\fi\the\day}
\date{\w3cdtfymd}
\renewcommand{\thefootnote}{\fnsymbol{footnote}}

\makeatletter
  \setlength\headheight{12\p@}
  \setlength\headsep   {.25in}
  \setlength\topskip   {10\p@}
  \setlength\footskip{.35in}
  \setlength\textwidth{400\p@}
  
  \setlength\@tempdima{\paperheight}
  \addtolength\@tempdima{-2in}
  \divide\@tempdima\baselineskip
  \@tempcnta=\@tempdima
  \setlength\textheight{\@tempcnta\baselineskip}
  \addtolength\textheight{\topskip}
  
  \setlength\@tempdima        {\paperwidth}
  \addtolength\@tempdima      {-\textwidth}
  \setlength\oddsidemargin    {\paperwidth}
  \addtolength\oddsidemargin  {-2.35in}
  \addtolength\oddsidemargin  {-\textwidth}
  \setlength\marginparwidth   {0pt}
  \@settopoint\oddsidemargin
  \@settopoint\marginparwidth
  \setlength\evensidemargin  {\paperwidth}
  \addtolength\evensidemargin{-2.35in}
  \addtolength\evensidemargin{-\textwidth}
  \@settopoint\evensidemargin
  
  \setlength\topmargin{\paperheight}
  \addtolength\topmargin{-2in}
  \addtolength\topmargin{-\headheight}
  \addtolength\topmargin{-\headsep}
  \addtolength\topmargin{-\textheight}
  \addtolength\topmargin{-\footskip}     % this might be wrong!
  \addtolength\topmargin{-.5\topmargin}
  \@settopoint\topmargin
\makeatother

\fancypagestyle{plain}{\fancyhf{}\fancyfoot[LE,RO]{\footnotesize\textbf\thepage}}
\pagestyle{plain}

\renewcommand{\headrulewidth}{0pt}
\renewcommand{\footrulewidth}{0pt}

\newcounter{img}[chapter]
\renewcommand{\theimg}{\thechapter.\arabic{img}}
\newcommand{\img}[1]{\begin{figure}[ht!]
	\refstepcounter{img}
	\label{img:\theimg}
	\centering\IfFileExists{figures/\theimg.pdf}{\includegraphics[width=\maxwidth]{figures/\theimg.pdf}}{\includegraphics[width=\maxwidth]{figures/\theimg.png}}

	\caption{#1}
\end{figure}}

\newcounter{tab}[chapter]
\renewcommand{\thetab}{\thechapter.\arabic{tab}}

\newcommand{\prechap}{<%= config['prechap'] %>}
\newcommand{\postchap}{<%= config['postchap'] %>}
\newcommand{\presect}{<%= config['presect'] %>}
\newcommand{\postsect}{<%= config['postsect'] %>}
\renewcommand{\chaptermark}[1]{\markboth{\textbf{\prechap \thechapter \postchap}\hspace*{1ex}#1}{}}
\renewcommand{\sectionmark}[1]{\markright{\textbf{\presect \thesection \postsect}\hspace*{1ex}#1}}
\newcommand{\chap}[1]{\newpage\thispagestyle{empty}\chapter{#1}\label{chap:\thechapter}}
\newcommand{\chapref}[1]{\hyperref[chap:#1]{\prechap #1\postchap}}
\newcommand{\imgref}[1]{\hyperref[img:#1]{<%= config['fig'] %>#1}}
\newcommand{\tabref}[1]{\hyperref[tab:#1]{<%= config['tab'] %>#1}}
\newcommand{\e}[1]{$ \times 10^{#1}$}
\renewcommand{\contentsname}{<%= config['con'] %>}
\renewcommand{\figurename}{<%= config['fig'] %>}
\renewcommand{\tablename}{<%= config['tab'] %>}

\makeatletter
\def\@makechapterhead#1{%
  \vspace*{50\p@}%
  {\parindent \z@ \raggedright \normalfont
    \ifnum \c@secnumdepth >\m@ne
      \if@mainmatter
        \huge\bfseries \prechap \thechapter \postchap
        \par\nobreak
        \vskip 20\p@
      \fi
    \fi
    \interlinepenalty\@M
    \Huge \bfseries #1\par\nobreak
    \vskip 40\p@
  }}
\makeatother

\linespread{1.3}

\begin{document}
\frontmatter
\maketitle
\thispagestyle{empty}
\setcounter{tocdepth}{4}
\tableofcontents\newpage\thispagestyle{empty}

\mainmatter
\fancyhf{}
\fancyhead[LE]{{\small\leftmark}}
\fancyhead[RO]{{\small\rightmark}}
\fancyhead[RE,LO]{{\small\textbf{\shorttitle}}}
\fancyfoot[LE,RO]{\small\textbf\thepage}
\pagestyle{fancy}

<%= latex %>
\end{document}
TEX

$texTemplate = ERB.new($texTemplateFile)

# Cleanup
FileUtils.rm_rf($outputDirectory)
$en = "#{$root}/en"
$figures = "#{$root}/figures"

#
# This generates anchors that work with redcarpets toc data option in Jekyll
#
def tocify(markdownFile, htmlFile)
  bookSiteUrl = $config['bookSiteUrl']
  tocCount = 0
  tocLines = ""
  File.open(markdownFile, 'r') do |f|
    f.each_line do |line|
      forbidden_words = ['Table of contents', 'define', 'pragma']
      next if !line.start_with?("#") || forbidden_words.any? { |w| line =~ /#{w}/ }
      title = line.gsub("#", "").strip
      id = title.downcase.gsub(" ", "-")
      
      href = title.gsub(" ", "-").downcase
      href = "#{bookSiteUrl}#{htmlFile}#toc_#{tocCount}"
      href = "#{bookSiteUrl}#{htmlFile}##{id}"
      tocLine = "    " * (line.count("#")-1) + "* [#{title}](#{href})\n"
      tocLines << tocLine
      tocCount = tocCount + 1
    end
  end
  return tocLines
end

chapters = []
Dir.glob("#{$bookContent}/[0-9][0-9]-*.md") { |file|
  chapters << file
}

#
# For each chapter file that looks like:
#
# 01-preface.md
#
# We want to write out the file to
#
# en/01-preface/01-chapter1.markdown
#
# We also want to emit a version of the chapter with Jekyll front-matter that can
# be integrated into a normal Jekyll site
#
toc = ""
bookDirectory = $outputDirectory
FileUtils.mkdir_p(bookDirectory)
chapters.each { |chapter|
  basename = File.basename(chapter, ".md")
  htmlFile = "#{basename}.html"
  parts = basename.match(/([0-9][0-9])-(.*)/)
  chapterNumber = parts[1]
  title = parts[2].capitalize
  chapterDirectory = "en/#{basename}"
  #
  # PDF/ebook content   # 
  FileUtils.mkdir_p chapterDirectory
  transformedChapterFile = "#{chapterDirectory}/#{chapterNumber}-chapter1.markdown"
  FileUtils.cp(chapter, transformedChapterFile)
  #
  # Jekyll content
  #
  jekyllPath = "#{bookDirectory}/#{basename}.md"
  content = File.open(chapter).read
  File.open(jekyllPath, 'w') { |f| 
    f.write("---\n")
    f.write("layout: chapter\n")
    f.write("title: #{title}\n")
    f.write("---\n")
    f.write(content) 
  }
  toc << tocify(chapter, htmlFile)
}

tocFile = "#{bookDirectory}/index.md"
File.open(tocFile, 'w') { |f| 
  f.write("---\n")
  f.write("layout: bookTOC\n")
  f.write("title: Index\n")
  f.write("---\n")
  f.write(toc) 
}

# ------------------------------------------------------------------------------
# PDF
# ------------------------------------------------------------------------------

def figures(lang,&block)
  begin
    Dir["#$root/figures/18333*.png"].each do |file|
      cp(file, file.sub(/18333fig0(\d)0?(\d+)\-tn/, '\1.\2'))
    end
    Dir["#$root/#{lang}/figures-dia/*.dia"].each do |file|
      eps_dest= file.sub(/.*fig0(\d)0?(\d+).dia/, '\1.\2.eps')
      system("dia -t eps-pango -e #$root/figures/#{eps_dest} #{file}")
      system("epstopdf #$root/figures/#{eps_dest}")
    end
    cp(Dir["#$root/#{lang}/figures/*.png"],"#$root/figures")
    cp(Dir["#$root/#{lang}/figures/*.pdf"],"#$root/figures")
    block.call
  ensure
    Dir["#$root/figures/18333*.png"].each do |file|
      rm(file.gsub(/18333fig0(\d)0?(\d+)\-tn/, '\1.\2'))
    end
    rm(Dir["#$root/figures/*.pdf"])
    rm(Dir["#$root/figures/*.eps"])
  end
end

def command_exists?(command)
  if File.executable?(command) then
    return command
  end
  ENV['PATH'].split(File::PATH_SEPARATOR).map do |path|
    cmd = "#{path}/#{command}"
    File.executable?(cmd) || File.executable?("#{cmd}.exe") || File.executable?("#{cmd}.cmd")
  end.inject{|a, b| a || b}
end

def replace(string, &block)
  string.instance_eval do
    alias :s :gsub!
    instance_eval(&block)
  end
  string
end

def verbatim_sanitize(string)
  string.gsub('\\', '{\textbackslash}').
    gsub('~', '{\textasciitilde}').
    gsub(/([\$\#\_\^\%])/, '\\\\' + '\1{}')
end

def pre_pandoc(string, config)
  replace(string) do
    # Pandoc discards #### subsubsections #### - this hack recovers them
    # be careful to try to match the longest sharp string first
    s /\#\#\#\#\# (.*?) \#\#\#\#\#/, 'PARAGRAPH: \1'
    s /\#\#\#\# (.*?) \#\#\#\#/, 'SUBSUBSECTION: \1'

    # Turns URLs into clickable links
    s /\`(http:\/\/[A-Za-z0-9\/\%\&\=\-\_\\\.\(\)\#]+)\`/, '<\1>'
    s /(\n\n)\t(http:\/\/[A-Za-z0-9\/\%\&\=\-\_\\\.\(\)\#]+)\n([^\t]|\t\n)/, '\1<\2>\1'

    # Match table in markdown and change them to pandoc's markdown tables
    s /(\n(\n\t([^\t\n]+)\t([^\t\n]+))+\n\n)/ do
      first_col=20
      t = $1.gsub /(\n?)\n\t([^\t\n]+)\t([^\t\n]+)/ do
        if $1=="\n"
          # This is the header, need to add the dash line
          $1 << "\n " << $2 << " "*(first_col-$2.length) << $3 <<
          "\n " << "-"*18 << "  " << "-"*$3.length
        else
          # Table row : format the first column as typewriter and align
          $1 << "\n `" << $2 << "`" + " "*(first_col-$2.length-2) << $3
        end
      end
      t << "\n"
    end

    # Process figures
    s /Insert\s18333fig\d+\.png\s*\n.*?\d{1,2}-\d{1,2}\. (.*)/, 'FIG: \1'
  end
end

def post_pandoc(string, config)
  replace(string) do
    space = /\s/

    # Reformat for the book documentclass as opposed to article
    s '\section', '\chap'
    s '\sub', '\\'
    s /SUBSUBSECTION: (.*)/, '\subsubsection{\1}'
    s /PARAGRAPH: (.*)/, '\paragraph{\1}'

    # Enable proper cross-reference
    s /#{config['fig'].gsub(space, '\s')}\s*(\d+)\-\-(\d+)/, '\imgref{\1.\2}'
    s /#{config['tab'].gsub(space, '\s')}\s*(\d+)\-\-(\d+)/, '\tabref{\1.\2}'
    s /#{config['prechap'].gsub(space, '\s')}\s*(\d+)(\s*)#{config['postchap'].gsub(space, '\s')}/, '\chapref{\1}\2'

    # Miscellaneous fixes
    s /FIG: (.*)/, '\img{\1}'
    s '\begin{enumerate}[1.]', '\begin{enumerate}'
    s /(\w)--(\w)/, '\1-\2'
    s /``(.*?)''/, "#{config['dql']}\\1#{config['dqr']}"

    # Typeset the maths in the book with TeX
    s '\verb!p = (n(n-1)/2) * (1/2^160))!', '$p = \frac{n(n-1)}{2} \times \frac{1}{2^{160}}$)'
    s '2\^{}80', '$2^{80}$'
    s /\sx\s10\\\^\{\}(\d+)/, '\e{\1}'

    # Convert inline-verbatims into \texttt (which is able to wrap)
    s /\\verb(\W)(.*?)\1/ ,'\\texttt{\2}'

    # Style ctables
    s /ctable\[pos = H, center, botcap\]\{..\}/ , 'ctable[pos = ht!, caption = ~ ,width = 130mm, center, botcap]{lX}'
    s /longtable\}\[c\]\{\@\{\}ll\@\{\}\}/ , 'longtable}[c]{@{}lp{10cm}@{}}
\caption{~}\\\\\\\\'

    # Shaded verbatim block
    s /(\\begin\{verbatim\}.*?\\end\{verbatim\})/m, '\begin{shaded}\1\end{shaded}'
  end
end

languages = $config['bookLanguages']

missing = ['pandoc', 'xelatex'].reject{|command| command_exists?(command)}
unless missing.empty?
  puts "Missing dependencies: #{missing.join(', ')}."
  puts "Install these and try again."
  exit
end

languages.each do |lang|
  figures(lang) do
    config = $config
    puts "#{lang}:"
    markdown = Dir["#$root/#{lang}/*/*.markdown"].sort.map do |file|
      File.read(file)
    end.join("\n\n")

    print "\tParsing markdown... "
    latex = IO.popen('pandoc -p --no-wrap -f markdown -t latex', 'w+') do |pipe|
      pipe.write(pre_pandoc(markdown, config))
      pipe.close_write
      post_pandoc(pipe.read, config)
    end
    puts "done"

    print "\tCreating main.tex for #{lang}... "
    dir = "#$root/#{lang}"
    mkdir_p(dir)
    File.open("#{dir}/main.tex", 'w') do |file|
      file.write($texTemplate.result(binding))
    end
    puts "done"

    abort = false

    puts "\tRunning XeTeX:"
    cd($root)
    3.times do |i|
      print "\t\tPass #{i + 1}... "
      IO.popen("xelatex -file-line-error -output-directory=\"#{dir}\" \"#{dir}/main.tex\" 2>&1") do |pipe|
        unless $DEBUG
          if $_[0..1]=='! '
            puts "failed with:\n\t\t\t#{$_.strip}"
            abort = true
          end while pipe.gets and not abort
        else
          STDERR.print while pipe.gets rescue abort = true
        end
      end
      break if abort
      puts "done"
    end

    unless abort
      print "\tMoving output to #{$bookFileName}.#{lang}.pdf... "
                        mv("#{dir}/main.pdf", "#$root/#{$bookFileName}.#{lang}.pdf")
      puts "done"
    end
  end
end

# ------------------------------------------------------------------------------
# Ebooks
# ------------------------------------------------------------------------------

def figures(lang,&block)
  begin
    Dir["figures/18333*.png"].each do |file|
      cp(file, file.sub(/18333fig0(\d)0?(\d+)\-tn/, '\1.\2'))
    end
    Dir["#{lang}/figures/*.png"].each do |file|
      cp(file,"figures")
    end
    Dir["#{lang}/figures-dia/*.dia"].each do |file|
      png_dest= file.sub(/.*fig0(\d)0?(\d+).dia/, 'figures/\1.\2.png')
      system("dia -e #{png_dest} #{file}")
    end
    block.call
  ensure
    Dir["figures/18333*.png"].each do |file|
      rm(file.gsub(/18333fig0(\d)0?(\d+)\-tn/, '\1.\2'))
    end
  end
end

$config['bookFormats'].each do |format|
  $config['bookLanguages'].each do |lang|
    figures (lang) do
      puts "convert content for '#{lang}' language"
      authors = $config['bookAuthor']
      book_content = %(<html xmlns="http://www.w3.org/1999/xhtml"><head><title>#{$config['bookTitle']}</title></head><body>)
      dir = File.expand_path(File.join(File.dirname(__FILE__), lang))
      Dir[File.join(dir, '**', '*.markdown')].sort.each do |input|
        puts "processing #{input}"
        content = File.read(input)
        content.gsub!(/Insert\s18333fig\d+\.png\s*\n.*?(\d{1,2})-(\d{1,2})\. (.*)/, '![\1.\2 \3](figures/\1.\2.png "\1.\2 \3")')
        book_content << RDiscount.new(content).to_html
      end
      book_content << "</body></html>"
  
      File.open("#{$bookFileName}.#{lang}.html", 'w') do |output|
        output.write(book_content)
      end
  
      $ebook_convert_cmd = ENV['ebook_convert_path'].to_s
      if $ebook_convert_cmd.empty?
        $ebook_convert_cmd = `which ebook-convert`.chomp
      end
      if $ebook_convert_cmd.empty?
        mac_osx_path = '/Applications/calibre.app/Contents/MacOS/ebook-convert'
        $ebook_convert_cmd = mac_osx_path
      end
      
      system($ebook_convert_cmd, "#{$bookFileName}.#{lang}.html", "#{$bookFileName}.#{lang}.#{format}",
             '--cover', $config['bookCover'],
             '--authors', $config['bookAuthor'],
             '--comments', $config['bookComments'],
             '--level1-toc', '//h:h1',
             '--level2-toc', '//h:h2',
             '--level3-toc', '//h:h3',
             '--language', lang)
    end
  end
end

# 
# Cleanup
#
mv("#{$bookFileName}.en.epub", "#{$outputDirectory}/#{$bookFileName}.epub")
mv("#{$bookFileName}.en.mobi", "#{$outputDirectory}/#{$bookFileName}.mobi")
mv("#{$bookFileName}.en.pdf", "#{$outputDirectory}/#{$bookFileName}.pdf")
rm("#{$bookFileName}.en.html")
FileUtils.rm_rf($en)
